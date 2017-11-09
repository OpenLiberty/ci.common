package net.wasdev.wlp.common.arquillian.objects;

public class WLPManagedObject {

	private final String wlpHome;
	private final String serverName;
	private final String httpPort;
	
	public WLPManagedObject(String wlpHome, String serverName, String httpPort) {
		this.wlpHome = wlpHome;
		this.serverName = serverName;
		this.httpPort = httpPort;
	}
	
}
