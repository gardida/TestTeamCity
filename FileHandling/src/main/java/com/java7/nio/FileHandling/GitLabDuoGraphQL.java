import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;

public class GitLabDuoChat {

    // CONFIGURATION
    private static final String GITLAB_HOST = "gitlab.com"; // or your self-managed instance
    private static final String ACCESS_TOKEN = "YOUR_PERSONAL_ACCESS_TOKEN";
    
    // ENDPOINTS
    private static final String GRAPHQL_URL = "https://" + GITLAB_HOST + "/api/graphql";
    private static final String WEBSOCKET_URL = "wss://" + GITLAB_HOST + "/-/cable";

    public static void main(String[] args) throws Exception {
        System.out.println("--- Starting GitLab Duo Chat Client ---");

        // 1. Generate a unique subscription ID for this specific chat session
        String clientSubscriptionId = UUID.randomUUID().toString();
        System.out.println("Generated Session ID: " + clientSubscriptionId);

        // 2. Initialize HTTP Client
        HttpClient client = HttpClient.newHttpClient();

        // 3. Connect to WebSocket (ActionCable) to listen for the answer
        CountDownLatch responseLatch = new CountDownLatch(1); // Keeps app running until answer received
        
        WebSocket ws = client.newWebSocketBuilder()
                .header("Origin", "https://" + GITLAB_HOST)
                .header("Authorization", "Bearer " + ACCESS_TOKEN) // Authenticate WebSocket
                .buildAsync(URI.create(WEBSOCKET_URL), new ActionCableListener(clientSubscriptionId, responseLatch))
                .join();

        // 4. Send the Chat Prompt via GraphQL (HTTP POST)
        String userPrompt = "Explain how to write a Hello World in Java.";
        sendChatMutation(client, userPrompt, clientSubscriptionId);

        // 5. Wait for the conversation to finish
        responseLatch.await();
        System.out.println("\n--- Chat Finished ---");
    }

    // --- HTTP: Sending the Prompt ---
    private static void sendChatMutation(HttpClient client, String prompt, String subscriptionId) {
        System.out.println("Sending prompt: \"" + prompt + "\"");

        // GraphQL Mutation to trigger the AI
        // We pass the SAME subscriptionId here so the server knows where to send the answer.
        String jsonBody = String.format(
            "{\"query\": \"mutation { aiAction(input: { chat: { resourceId: \\\"gid://gitlab/User/1\\\", content: \\\"%s\\\" }, clientSubscriptionId: \\\"%s\\\" }) { errors } }\"}",
            escapeJson(prompt), subscriptionId
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GRAPHQL_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + ACCESS_TOKEN)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(response -> {
                    if (response.contains("errors") && !response.contains("\"errors\":[]")) {
                        System.err.println("API Error: " + response);
                    } else {
                        System.out.println("Prompt sent successfully. Waiting for stream...");
                    }
                });
    }

    // --- WebSocket: Handling the Stream (ActionCable Protocol) ---
    private static class ActionCableListener implements WebSocket.Listener {
        private final String subscriptionId;
        private final CountDownLatch latch;
        private StringBuilder messageBuffer = new StringBuilder();

        public ActionCableListener(String subscriptionId, CountDownLatch latch) {
            this.subscriptionId = subscriptionId;
            this.latch = latch;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            System.out.println("WebSocket Connected.");
            
            // ActionCable Handshake: Subscribe to the AI Completion Channel
            // The channel name is specific to GitLab's implementation
            String subscribeMsg = String.format(
                "{\"command\":\"subscribe\",\"identifier\":\"{\\\"channel\\\":\\\"AiCompletionChannel\\\",\\\"client_subscription_id\\\":\\\"%s\\\", \\\"html_id\\\": \\\"div-id\\\"}\"}", 
                subscriptionId
            );
            webSocket.sendText(subscribeMsg, true);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            messageBuffer.append(data);
            if (last) {
                String msg = messageBuffer.toString();
                messageBuffer.setLength(0); // Reset buffer
                handleMessage(msg, webSocket);
            }
            return WebSocket.Listener.super.onText(webSocket, data, last);
        }

        private void handleMessage(String msg, WebSocket ws) {
            // Ignore pings (heartbeats)
            if (msg.contains("\"type\":\"ping\"") || msg.contains("\"type\":\"welcome\"") || msg.contains("\"type\":\"confirm_subscription\"")) {
                return;
            }

            // Simple parsing to extract content (In a real app, use a JSON library like Jackson/Gson)
            if (msg.contains("\"content\":")) {
                String content = extractContent(msg);
                if (content != null) {
                    System.out.print(content); // Stream the text to console
                }
            }

            // Check for end of stream
            if (msg.contains("chunkId") && msg.contains("null") && msg.contains("role")) {
                 // Often the last message or a specific "done" signal. 
                 // For this simple example, we assume if we stop getting data for a bit, we are done,
                 // or you can implement a timeout.
            }
        }
    }

    // Helper: Basic manual JSON escaping for the prompt
    private static String escapeJson(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }
    
    // Helper: Dirty hack to extract "content" from JSON without a library
    private static String extractContent(String json) {
        try {
            int contentIndex = json.indexOf("\\\"content\\\":\\\"");
            if (contentIndex == -1) return null;
            int start = contentIndex + 14;
            int end = json.indexOf("\\\"", start);
            String content = json.substring(start, end);
            
            // Clean up escaped newlines and characters
            return content.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"");
        } catch (Exception e) {
            return null;
        }
    }
}
