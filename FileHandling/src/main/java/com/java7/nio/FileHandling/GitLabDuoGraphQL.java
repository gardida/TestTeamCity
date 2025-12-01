import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class GitLabDuoChat {

    // --- CONFIGURATION ---
    private static final String GITLAB_HOST = "gitlab.com"; 
    private static final String ACCESS_TOKEN = "YOUR_PERSONAL_ACCESS_TOKEN"; 
    
    // --- CONSTANTS ---
    private static final String GRAPHQL_URL = "https://" + GITLAB_HOST + "/api/graphql";
    private static final String WEBSOCKET_URL = "wss://" + GITLAB_HOST + "/-/cable";

    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        // 1. Fetch Current User ID (Context is required for Chat)
        String currentUserId = fetchCurrentUserId(client);
        System.out.println("✅ Authenticated as: " + currentUserId);

        String clientSubscriptionId = UUID.randomUUID().toString();
        System.out.println("✅ Session ID: " + clientSubscriptionId);

        // 2. Latches for Synchronization
        // We must not send the chat prompt until the socket says "Subscription Confirmed"
        CountDownLatch subscriptionConfirmedLatch = new CountDownLatch(1);
        CountDownLatch responseReceivedLatch = new CountDownLatch(1);

        // 3. Connect WebSocket
        System.out.println("⏳ Connecting to WebSocket...");
        ActionCableListener listener = new ActionCableListener(clientSubscriptionId, subscriptionConfirmedLatch, responseReceivedLatch);
        
        WebSocket ws = client.newWebSocketBuilder()
                .header("Origin", "https://" + GITLAB_HOST)
                .header("Authorization", "Bearer " + ACCESS_TOKEN)
                .buildAsync(URI.create(WEBSOCKET_URL), listener)
                .join();

        // 4. WAIT for Subscription Confirmation (Max 10 seconds)
        boolean isSubscribed = subscriptionConfirmedLatch.await(10, TimeUnit.SECONDS);
        if (!isSubscribed) {
            System.err.println("❌ Failed to subscribe to ActionCable channel. Check Token/Permissions.");
            System.exit(1);
        }
        System.out.println("✅ WebSocket Subscription Confirmed!");

        // 5. Send Prompt
        String userPrompt = "Generate a Java class for a Binary Search Tree";
        sendChatMutation(client, userPrompt, clientSubscriptionId, currentUserId);

        // 6. Wait for the answer stream to finish
        responseReceivedLatch.await(30, TimeUnit.SECONDS);
        System.out.println("\n--- Chat Session Ended ---");
    }

    // --- HTTP: Fetch User ID ---
    private static String fetchCurrentUserId(HttpClient client) {
        String query = "{\"query\": \"query { currentUser { id } }\"}";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GRAPHQL_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + ACCESS_TOKEN)
                    .POST(HttpRequest.BodyPublishers.ofString(query))
                    .build();

            String response = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
            
            // Quick/Dirty Parse to find "gid://gitlab/User/..."
            int start = response.indexOf("gid://gitlab/User/");
            if (start == -1) throw new RuntimeException("Could not fetch User ID. Check Token.");
            int end = response.indexOf("\"", start);
            return response.substring(start, end);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get User ID", e);
        }
    }

    // --- HTTP: Send Prompt ---
    private static void sendChatMutation(HttpClient client, String prompt, String subscriptionId, String resourceId) {
        System.out.println("⏳ Sending prompt to API...");
        
        // Note: resourceId is now dynamic
        String jsonBody = String.format(
            "{\"query\": \"mutation { aiAction(input: { chat: { resourceId: \\\"%s\\\", content: \\\"%s\\\" }, clientSubscriptionId: \\\"%s\\\" }) { errors } }\"}",
            resourceId, escapeJson(prompt), subscriptionId
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GRAPHQL_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + ACCESS_TOKEN)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(resp -> {
                   if(resp.body().contains("\"errors\":[]")) {
                       System.out.println("✅ Prompt accepted by server. Waiting for stream...");
                   } else {
                       System.err.println("❌ API Error: " + resp.body());
                   }
                });
    }

    // --- WebSocket Listener ---
    private static class ActionCableListener implements WebSocket.Listener {
        private final String subscriptionId;
        private final CountDownLatch subscribeLatch;
        private final CountDownLatch completionLatch;
        private StringBuilder buffer = new StringBuilder();

        public ActionCableListener(String subId, CountDownLatch subLatch, CountDownLatch compLatch) {
            this.subscriptionId = subId;
            this.subscribeLatch = subLatch;
            this.completionLatch = compLatch;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            // Subscribe Command
            String subscribeMsg = String.format(
                "{\"command\":\"subscribe\",\"identifier\":\"{\\\"channel\\\":\\\"AiCompletionChannel\\\",\\\"client_subscription_id\\\":\\\"%s\\\", \\\"html_id\\\": \\\"div-id\\\"}\"}", 
                subscriptionId
            );
            webSocket.sendText(subscribeMsg, true);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String msg = buffer.toString();
                buffer.setLength(0);
                processMessage(msg);
            }
            return WebSocket.Listener.super.onText(webSocket, data, last);
        }

        private void processMessage(String msg) {
            // 1. Check for Confirmation
            if (msg.contains("\"type\":\"confirm_subscription\"")) {
                subscribeLatch.countDown();
                return;
            }
            
            // 2. Check for Rejection
            if (msg.contains("\"type\":\"reject_subscription\"")) {
                System.err.println("❌ Subscription Rejected! Protocol or Permissions error.");
                System.err.println("Raw: " + msg);
                System.exit(1);
            }

            // 3. Process Content
            if (msg.contains("\"content\":")) {
                String content = extractContent(msg);
                if (content != null) System.out.print(content);
            }
            
            // 4. Detect Stream End (Duo usually sends an empty chunk or 'role' at the end)
            // This is a heuristic; production apps track the 'chunkId'
            if (msg.contains("\"content\":\"\"") || (msg.contains("chunkId") && msg.contains("null"))) {
                // completionLatch.countDown(); // Uncomment to auto-exit
            }
        }
    }

    private static String escapeJson(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static String extractContent(String json) {
        try {
            int idx = json.indexOf("\\\"content\\\":\\\"");
            if (idx == -1) return null;
            int start = idx + 14;
            int end = json.indexOf("\\\"", start);
            String raw = json.substring(start, end);
            return raw.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\");
        } catch (Exception e) { return null; }
    }
}
