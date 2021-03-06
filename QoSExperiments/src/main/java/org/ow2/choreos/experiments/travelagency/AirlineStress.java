package org.ow2.choreos.experiments.travelagency;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
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
import org.ow2.choreos.services.datamodel.qos.DesiredQoS.ScalePolicy;
import org.ow2.choreos.services.datamodel.qos.ResponseTimeMetric;
import org.ow2.choreos.tests.ModelsForTest;

public class AirlineStress implements Runnable {

	Logger logger = Logger.getLogger("expLogger");

	// pagina principal PT
	private static final double rateVectorEx1[][] = { 
			{ 6348, 5 }, { 6139, 5 }, { 4861, 5 }, { 3910, 5 }, 
			{ 2638, 5 }, { 1754, 5 }, { 1173, 5 }, { 877 , 5 },
			{ 913 , 5 }, { 1125, 5 }, { 1761, 5 }, { 2668, 5 }, 
			{ 4019, 5 }, { 4943, 5 }, { 5191, 5 }, { 5926, 5 }, 
			{ 2208, 5 }, { 6311, 5 }, { 6633, 5 }, { 6347, 5 },
			{ 1615, 5 }, { 7069, 5 }, { 6816, 5 }, { 7077, 5 }};
	
	

	// Wikipedia: Your First Article EN - 2/9
//	private static final double rateVectorEx1[][] = { 
//		{ 19846, 5 }, { 17140, 5 }, { 14692, 5 }, { 14233, 5 }, 
//		{ 13848, 5 }, { 14441, 5 }, { 13891, 5 }, { 12845, 5 },
//		{ 11581, 5 }, { 10006, 5 }, { 10150, 5 }, { 9330 , 5 }, 
//		{ 9008 , 5 }, { 9157 , 5 }, { 9117 , 5 }, { 9383 , 5 }, 
//		{ 10047, 5 }, { 9935 , 5 }, { 9683 , 5 }, { 10116, 5 },
//		{ 9554 , 5 }, { 12734, 5 }, { 10163, 5 }, { 8841 , 5 }};
	
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
	}

	public AirlineStress() {
		pool = Executors.newFixedThreadPool(N_TRDS);

		ResourceImpact airlineResourceImpact = new ResourceImpact();
		airlineResourceImpact.setCpu(CPUSize.SMALL);
		airlineResourceImpact.setRAM(RAMSize.MEDIUM);
		models = new ModelsForTest(ServiceType.SOAP, PackageType.TOMCAT,
				airlineResourceImpact);
		chorSpec = models.getChorSpec();

		ResourceImpact travelagencyResourceImpact = new ResourceImpact();
		travelagencyResourceImpact.setCpu(CPUSize.MEDIUM); // to prevent tomcat
															// restart

		((DeployableServiceSpec) chorSpec
				.getServiceSpecByName(ModelsForTest.TRAVEL_AGENCY))
				.setResourceImpact(travelagencyResourceImpact);

		Properties resourceParams = new Properties();
		resourceParams.setProperty("cpu_max", "85.0");
		resourceParams.setProperty("cpu_min", "15.0");
		chorSpec.setResourceParams(resourceParams);

		DesiredQoS desiredQoS = new DesiredQoS();
		desiredQoS.setScalePolicy(ScalePolicy.HORIZONTAL);
		ResponseTimeMetric responseTime = new ResponseTimeMetric();
		responseTime.setAcceptablePercentage(0.05f);
		responseTime.setMaxDesiredResponseTime(1000f);
		desiredQoS.setResponseTimeMetric(responseTime);

		((DeployableServiceSpec) chorSpec
				.getServiceSpecByName(ModelsForTest.AIRLINE))
				.setDesiredQoS(desiredQoS);

//		((DeployableServiceSpec) chorSpec
//				.getServiceSpecByName(ModelsForTest.AIRLINE))
//				.setNumberOfInstances(2);
	}

	private void runExperiment() throws InterruptedException, IOException,
			DeploymentException, ChoreographyNotFoundException {

		String chorId = enactmentEngine.createChoreography(chorSpec);
		chor = enactmentEngine.deployChoreography(chorId);

		String travelAgencyURI = ((DeployableService) chor
				.getDeployableServiceBySpecName(ModelsForTest.TRAVEL_AGENCY))
				.getInstances().get(0).getNativeUri();

		long TIME_UNIT_IN_MS = 1000 * 60; // 30 for half of minute; 60 for
											// minute

		logger.info("Setting up travel agency clients. Total: " + N_TRDS);

		String travelAgencyWsdlLocation = travelAgencyURI + "?wsdl";
		for (int i = 0; i < N_TRDS; i++) {
			clients.add(getNewTravelAgencyClient(travelAgencyWsdlLocation));
		}

		double totalTime = 0;
		for (int j = 0; j < rateVectorEx1.length; j++) {
			totalTime += rateVectorEx1[j][1];
		}

		System.out.println("Total experiment time: " + totalTime
				+ ". Press ENTER to start experiment:");
		System.in.read();
		logger.info("Experiment started");

		for (int j = 0; j < rateVectorEx1.length; j++) {

			// Considering 1 hours as 5 minutes. 60/5=12
			double numberOfRequests = rateVectorEx1[j][0] / 12;
			
			// time to execute "numberOfRequests" requests
			double time = rateVectorEx1[j][1] * TIME_UNIT_IN_MS; // in milliseconds
			
			double waitingTimeUntilNextRequest = time / numberOfRequests;
			
			
			double timeCounter = 0;
			long num = (long) (waitingTimeUntilNextRequest);

			logger.info("Starting loop; rate = " + numberOfRequests + "ms; time = " + time);			
			while (timeCounter < time) {
				timeCounter += num;
				pool.submit(new BuyTripHandler(getClient(), numberOfRequests));
				Thread.sleep(num);
			}
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
		private double rate;

		public BuyTripHandler(TravelAgencyService client, double rate) {
			this.client = client;
			this.rate = rate;
		}

		@Override
		public String call() {
			long t = System.currentTimeMillis();
			String result;
			try {
				result = client.buyTrip();
			} catch (Exception e) {
				if (e instanceof SocketTimeoutException)
					System.out.println("Rate: " + rate + "; Timed out");
				return "timed out";
			}
			long tf = System.currentTimeMillis() - t;
			String res = "Rate: " + rate + "; RT " + (tf) + " >> Result = " + result;
			System.out.println(res);
			return res;
		}
	}

	public static void main(String[] args) {
		new Thread(new AirlineStress()).start();
	}
}
