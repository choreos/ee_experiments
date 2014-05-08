package org.ow2.choreos.experiments.futuremarket;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.ow2.choreos.chors.datamodel.Choreography;
import org.ow2.choreos.services.datamodel.Service;

import br.usp.ime.futuremarket.choreography.Portal;

public abstract class AbstractPortalProxy {
	
	private static Logger logger = Logger.getLogger(AbstractPortalProxy.class);
	
	private List<String> portals = new ArrayList<String>();
	protected Choreography choreography;

	public AbstractPortalProxy(Choreography chor) {
		this.choreography = chor;
	}

	public void setPortals(final Choreography chor)
			throws IOException {
		List<Service> services = chor.getServices();
		
		for (Service s: services) {
			if (s.getSpec().getRoles().contains("portal")) {
				logger.info("Portal URIs = " + s.getUris());
				portals.addAll(s.getUris());
			} else {
				logger.warn("Oooops! There is no portal!");
			}
		}
	}

	public abstract Portal getPortal() throws MalformedURLException;

	public int size() {
		return portals.size();
	}
}