package org.ow2.choreos.experiments.futuremarket;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.namespace.QName;
import javax.xml.ws.Service;

import org.ow2.choreos.chors.datamodel.Choreography;

import br.usp.ime.futuremarket.choreography.Portal;

public class PortalProxy extends AbstractPortalProxy {
	
	AtomicInteger index = new AtomicInteger(0);
	
	public PortalProxy(Choreography chor) throws IOException {
		super(chor);
		setPortals(chor);
	}

	@Override
	public Portal getPortal() throws MalformedURLException {
		String wsdl = "";
		
		List<org.ow2.choreos.services.datamodel.Service> services = choreography.getServices();
		
		for (org.ow2.choreos.services.datamodel.Service service: services) {
			if (service.getSpec().getRoles().contains("portal")) {
				wsdl = service.getUris().get(index.intValue() % service.getUris().size()) + "?wsdl";
				index.getAndDecrement();
			}
		}
		
		return new PortalProxyFactory(wsdl).getClient();
	}
	
	private static class PortalProxyFactory {
		
		static final String NAMESPACE = "http://futuremarket.ime.usp.br/choreography/portal";
		static final String SERVICE_NAME = "PortalImplService";
		
		String wsdl;
		Portal client = null;
		
		public PortalProxyFactory(final String wsdl) {
			this.wsdl = wsdl;
		}
		
		public Portal getClient() throws MalformedURLException {
			if (client ==  null) {
				synchronized (this) {
					final QName qname = new QName(NAMESPACE, SERVICE_NAME);
					final URL url = new URL(wsdl);
			        Service service = Service.create(url, qname);
			        client = service.getPort(Portal.class);
				}
			}
			return client;
		}
	}

}