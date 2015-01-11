package org.ow2.choreos.experiments.travelagency;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.namespace.QName;

import org.apache.log4j.Logger;
import org.ow2.choreos.chors.ChoreographyNotFoundException;
import org.ow2.choreos.chors.DeploymentException;
import org.ow2.choreos.chors.client.EEClient;
import org.ow2.choreos.chors.datamodel.Choreography;
import org.ow2.choreos.chors.datamodel.ChoreographySpec;
import org.ow2.choreos.experiments.travelagency.client.TravelAgencyService;
import org.ow2.choreos.experiments.travelagency.client.TravelAgencyServiceService;
import org.ow2.choreos.nodes.datamodel.CPUSize;
import org.ow2.choreos.nodes.datamodel.RAMSize;
import org.ow2.choreos.nodes.datamodel.ResourceImpact;
import org.ow2.choreos.services.datamodel.DeployableService;
import org.ow2.choreos.services.datamodel.DeployableServiceSpec;
import org.ow2.choreos.services.datamodel.PackageType;
import org.ow2.choreos.services.datamodel.ServiceType;
import org.ow2.choreos.services.datamodel.qos.DesiredQoS;
import org.ow2.choreos.services.datamodel.qos.ResponseTimeMetric;
import org.ow2.choreos.services.datamodel.qos.DesiredQoS.ScalePolicy;
import org.ow2.choreos.tests.ModelsForTest;

public class VerticalAirlineStress implements Runnable {

	Logger logger = Logger.getLogger("expLogger");
	
	private static final long rateVectorEx1[][] = { 
		{ 600, 10 }, 
		{ 500, 10 }, 
		{ 450, 10 }, 
		{ 400, 15 }, 
		{ 375, 15 }, 
		{ 350, 15 }, 
		{ 300, 15 }, 
		{ 275, 15 }, 
		{ 250, 15 },
		{ 300, 15 }, 
		{ 350, 15 }, 
		{ 375, 15 }, 
		{ 390, 15 }, 
		{ 400, 20 }, 
		{ 450, 20 }, 
		{ 500, 30 }, 
		{ 600, 30 }, 
		{ 700, 30 } 
	};

	private static final int N_TRDS = 200;
	private static EEClient enactmentEngine;

	private ChoreographySpec chorSpec;
	private ModelsForTest models;
	private Choreography chor;

	private AtomicInteger counter = new AtomicInteger(0);
	private List<TravelAgencyService> clients = new ArrayList<TravelAgencyService>();

	private final ExecutorService pool;

	static {
		enactmentEngine = new EEClient("http://localhost:9100/enactmentengine/");
		
		ModelsForTest.AIRLINE_WAR = "http://www.ime.usp.br/~tfurtado/downloads/airline.war";
		ModelsForTest.TRAVEL_AGENCY_WAR = "http://www.ime.usp.br/~tfurtado/downloads/travelagency.war";
		
		//ModelsForTest.AIRLINE_WAR = "http://thiagofurtado.com/airline.war";
		//ModelsForTest.TRAVEL_AGENCY_WAR = "http://thiagofurtado.com/travelagency.war";
	}

	public VerticalAirlineStress() {
		pool = Executors.newFixedThreadPool(N_TRDS);

		ResourceImpact ri = new ResourceImpact();
		ri.setCpu(CPUSize.SMALL);
		ri.setRAM(RAMSize.SMALL);
		models = new ModelsForTest(ServiceType.SOAP, PackageType.TOMCAT, ri);
		chorSpec = models.getChorSpec();
		
		Properties resourceParams = new Properties();
		resourceParams.setProperty("cpu_max", "90.0");
		resourceParams.setProperty("cpu_min", "60.0");
		chorSpec.setResourceParams(resourceParams);
		
		DesiredQoS desiredQoS = new DesiredQoS();
		ResponseTimeMetric responseTime = new ResponseTimeMetric();
		responseTime.setAcceptablePercentage(0.05f);
		responseTime.setMaxDesiredResponseTime(700f);
		desiredQoS.setResponseTimeMetric(responseTime);
		
		((DeployableServiceSpec) chorSpec.getServiceSpecByName("airline"))
				.setDesiredQoS(desiredQoS);

	}

	private void runExperiment() throws InterruptedException, IOException,
			DeploymentException, ChoreographyNotFoundException {

		String chorId = enactmentEngine.createChoreography(chorSpec);
		chor = enactmentEngine.deployChoreography(chorId);

		String travelAgencyURI = ((DeployableService) chor
				.getDeployableServiceBySpecName(ModelsForTest.TRAVEL_AGENCY))
				.getInstances().get(0).getNativeUri();

		long TIME_UNIT_IN_MS = 1000 * 6 * 1;

		logger.info("Setting up travel agency clients. Total: " + N_TRDS);

		String travelAgencyWsdlLocation = travelAgencyURI + "?wsdl";
		for (int i = 0; i < N_TRDS; i++) {
			clients.add(getNewTravelAgencyClient(travelAgencyWsdlLocation));
		}

		System.out.println("Press ENTER to start experiment:");
		System.in.read();
		logger.info("Experiment started");

		for (int j = 0; j < rateVectorEx1.length; j++) {

			long rate = rateVectorEx1[j][0];
			long time = rateVectorEx1[j][1];

			long timeCounter = 0;

			logger.info("Starting loop; rate = " + rate + "ms; time = " + time);
			while (timeCounter < time * TIME_UNIT_IN_MS) {
				timeCounter += rate;
				pool.submit(new BuyTripHandler(getClient()));
				logger.info("Request sent; sleeping for " + rate);
				Thread.sleep(rate);
			}
			logger.info("Finished loop; rate = " + rate + "ms; time = " + time);
		}

		logger.info("Experiment finished");
	}

	private TravelAgencyService getClient() {
		return clients.get(counter.getAndIncrement() % 10);
	}

	private TravelAgencyService getNewTravelAgencyClient(
			String travelAgencyWsdlLocation) throws MalformedURLException {
		String namespace = "http://choreos.ow2.org/";
		String local = "TravelAgencyServiceService";

		QName travelAgencyNamespace = new QName(namespace, local);
		TravelAgencyServiceService travel = new TravelAgencyServiceService(
				new URL(travelAgencyWsdlLocation), travelAgencyNamespace);

		return travel.getTravelAgencyServicePort();
	}

	@Override
	public void run() {
		try {
			runExperiment();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (DeploymentException e) {
			e.printStackTrace();
		} catch (ChoreographyNotFoundException e) {
			e.printStackTrace();
		}
	}

	class BuyTripHandler implements Callable<String> {
		

		private TravelAgencyService client;

		public BuyTripHandler(TravelAgencyService client) {
			this.client = client;
		}

		@Override
		public String call() throws Exception {
			long t = System.currentTimeMillis();
			String result = client.buyTrip();
			long tf = System.currentTimeMillis() - t;
			String res = result + "; " + (tf);
			System.out.println("res + " + res);
			return res;
		}

	}

	public static void main(String[] args) {
		new Thread(new VerticalAirlineStress()).start();
	}
}
