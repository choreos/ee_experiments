package org.ow2.choreos.experiments.travelagency.client;

import java.net.MalformedURLException;
import java.net.URL;

import javax.xml.namespace.QName;
import javax.xml.ws.Service;

import br.usp.ime.TravelAgency;

public class TravelAgencyClientOld {

	final private String namespace = "http://choreos.ow2.org/";
	final private String serviceName = "TravelAgencyServiceService";

	public TravelAgency getClient(final String wsdl)
			throws MalformedURLException {
		return this.getService(namespace, serviceName, wsdl).getPort(
				new QName(namespace, "TravelAgencyServicePort"),
				TravelAgency.class);
	}

	private Service getService(final String namespace,
			final String serviceName, final String wsdl)
			throws MalformedURLException {
		final QName qname = new QName(namespace, serviceName);
		final URL url = new URL(wsdl);
		return Service.create(url, qname);
	}

	public static void main(String[] args) {

		String wsdl = "http://10.0.0.27:8080/cb0df69a-8777-4a4c-baef-354640d4e0ef/travelagency?wsdl";

		try {
			TravelAgency ta = new TravelAgencyClientOld().getClient(wsdl);

			System.out.println(ta.buyTrip());
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
