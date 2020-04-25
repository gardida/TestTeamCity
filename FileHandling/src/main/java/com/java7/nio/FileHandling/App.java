package com.java7.nio.FileHandling;

import java.lang.management.ManagementFactory;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

/**
 * Hello world!
 *
 */
public class App {
	
	public static volatile boolean shutdown = false;
	
	public static void main(String[] args) {

		final String mbeanObjectNameStr = "example:type=ShutDownImpl";

		final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		registerMBean(mbs, mbeanObjectNameStr, ShutDownImpl.class);

		
		FileSearch search = new FileSearch();
		search.startFileMonitor();
	}

	public static void registerMBean(final MBeanServer mbs, final String mBeanObjectName, final Class mBeanClass) {
		try {
			final ObjectName name = new ObjectName(mBeanObjectName);
			final Object mBean = mBeanClass.newInstance();
			mbs.registerMBean(mBean, name);
		} catch (InstantiationException badInstance) // Class.newInstance()
		{
			System.err.println("Unable to instantiate provided class with class " + "name " + mBeanClass.getName()
					+ ":\n" + badInstance.getMessage());
		} catch (IllegalAccessException illegalAccess) // Class.newInstance()
		{
			System.err.println("Illegal Access trying to instantiate " + mBeanClass.getName() + ":\n"
					+ illegalAccess.getMessage());
		} catch (MalformedObjectNameException badObjectName) {
			System.err.println(mBeanObjectName + " is a bad ObjectName:\n" + badObjectName.getMessage());
		} catch (InstanceAlreadyExistsException duplicateMBeanInstance) {
			System.err
					.println(mBeanObjectName + " already existed as an MBean:\n" + duplicateMBeanInstance.getMessage());
		} catch (MBeanRegistrationException mbeanRegistrationProblem) {
			System.err.println(
					"ERROR trying to register " + mBeanObjectName + ":\n" + mbeanRegistrationProblem.getMessage());
		} catch (NotCompliantMBeanException badMBean) {
			System.err.println("ERROR: " + mBeanObjectName + " is not compliant:\n" + badMBean.getMessage());
		}
	}

}
