/*
 * copyright bradley childs (bchilds@gmail.com)


 * 
 * all rights reserved.
 * 
 */
package org.bc.eclipse.hadoop.debug;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.internal.launching.LaunchingMessages;
import org.eclipse.jdt.internal.launching.LaunchingPlugin;
import org.eclipse.jdt.internal.launching.SocketListenConnector;
import org.eclipse.jdt.internal.launching.SocketListenConnectorProcess;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.osgi.util.NLS;

import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.ListeningConnector;

/**
 * A retry socket listening connector. Starts a launch that waits for a VM to connect at a specific port.
 
 */

public class AllowRetrySocketListenConnector extends SocketListenConnector  {

	public void connect(Map<String, String> arguments, IProgressMonitor monitor, ILaunch launch) throws CoreException {
		if (monitor == null) {
			monitor = new NullProgressMonitor();
		}

		monitor.subTask(LaunchingMessages.SocketListenConnector_2);

		ListeningConnector connector = getListeningConnector();

		final String portNumberString = arguments.get("port"); //$NON-NLS-1$
		if (portNumberString == null) {
			abort(LaunchingMessages.SocketAttachConnector_Port_unspecified_for_remote_connection__2, null, IJavaLaunchConfigurationConstants.ERR_UNSPECIFIED_PORT);
		}

		Map<String, Connector.Argument> acceptArguments = connector.defaultArguments();

		Connector.Argument param = acceptArguments.get("port"); //$NON-NLS-1$
		param.setValue(portNumberString);

		try {
			monitor.subTask(NLS.bind(LaunchingMessages.SocketListenConnector_3, new String[] { portNumberString }));
			connector.startListening(acceptArguments);
			SocketListenConnectorProcess process = new SocketListenConnectorProcess(launch, portNumberString);

			launch.addProcess(process);
			process.waitForConnection(connector, acceptArguments);

			ILaunchConfiguration configuration = launch.getLaunchConfiguration();
			boolean relaunchTerminate = false;
			if (configuration != null) {
				try {
					relaunchTerminate = configuration.getAttribute("relaunchTerminate", false); //$NON-NLS-1$
				}
				catch (CoreException e) {
					LaunchingPlugin.log(e);
				}
			}

			if (relaunchTerminate) {
			IDebugEventSetListener listener = new IDebugEventSetListener() {
				public void reconnect(ILaunch launch) throws CoreException {
					ListeningConnector connector = getListeningConnector();
					Map<String, Connector.Argument> acceptArguments = connector.defaultArguments();
					Connector.Argument port = acceptArguments.get("port"); //$NON-NLS-1$
					SocketListenConnectorProcess process = new SocketListenConnectorProcess(launch, port.value());
					launch.addProcess(process);
					process.waitForConnection(connector, acceptArguments);
				}

				public void handleDebugEvents(DebugEvent[] event) {
					if (event[0].getSource() instanceof SocketListenConnectorProcess && event[0].getKind() == DebugEvent.TERMINATE) {
						SocketListenConnectorProcess p = (SocketListenConnectorProcess) event[0].getSource();
						try {
							ILaunch launch = p.getLaunch();
							launch.removeProcess(p);
							reconnect(launch);

						}
						catch (CoreException e) {
							DebugPlugin.getDefault().removeDebugEventListener(this);
						}

					}
				}

			};
			DebugPlugin.getDefault().addDebugEventListener(listener);
			}

		}
		catch (IOException e) {
			abort(LaunchingMessages.SocketListenConnector_4, e, IJavaLaunchConfigurationConstants.ERR_REMOTE_VM_CONNECTION_FAILED);
		}
		catch (IllegalConnectorArgumentsException e) {
			abort(LaunchingMessages.SocketListenConnector_4, e, IJavaLaunchConfigurationConstants.ERR_REMOTE_VM_CONNECTION_FAILED);
		}
	}
	public String getName() {
		return "restartable socket listen connector";
	}
	
	public String getIdentifier() {
		return "org.bc.eclipse.hadoop.debug.launching.AllowRetrySocketListenConnector";
	}
}
