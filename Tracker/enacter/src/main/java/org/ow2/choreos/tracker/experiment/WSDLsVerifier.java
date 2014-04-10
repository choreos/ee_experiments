package org.ow2.choreos.tracker.experiment;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;
import org.ow2.choreos.chors.datamodel.Choreography;
import org.ow2.choreos.services.datamodel.DeployableService;
import org.ow2.choreos.services.datamodel.Service;
import org.ow2.choreos.tracker.Enacter;
import org.ow2.choreos.utils.Concurrency;

public class WSDLsVerifier implements Runnable {
    
    private static Logger logger = Logger.getLogger(ChorVerifier.class);

    private static final int MAX_THREADS = 200;

    private Enacter enacter;
    private int chorsQty;
    
    // output
    AtomicInteger servicesWorking = new AtomicInteger();
    long time;

    public WSDLsVerifier(Enacter enacter, int chorsQty) {
        this.enacter = enacter;
        this.chorsQty = chorsQty;
    }

    /**
     * returns the amount of accessible WSDLs
     */
    @Override
    public void run() {
        long t0 = System.nanoTime();
        int NUM_THREADS = MAX_THREADS / chorsQty;
        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        Choreography chor = enacter.getChoreography();
        int len = chor.getDeployableServices().size();
        logger.info("Verifying " + len + " services in enacter " + enacter.getId());
        for (DeployableService svc : chor.getDeployableServices()) {
            String wsdl = getWsdl(svc);
            VerifierTask task = new VerifierTask(wsdl);
            executor.submit(task);
        }
        logger.info("Waiting for WSDL verifiers");
        Concurrency.waitExecutor(executor, Experiment.VERIFY_WSDLS_TIMEOUT, TimeUnit.MINUTES, logger,
                "Service per service verification did not work properly.");
        logger.info("Waiting no more for WSDL verifiers");
        long tf = System.nanoTime();
        time = tf - t0;
    }

    private String getWsdl(Service svc) {
        String uri = svc.getUris().get(0);
        String wsdl = uri.replaceAll("/$", "").concat("?wsdl");
        return wsdl;
    }
    
    private class VerifierTask implements Callable<Void> {

        String wsdl;
        
        public VerifierTask(String wsdl) {
            this.wsdl = wsdl;
        }

        @Override
        public Void call() throws Exception {
            WSDLChecker checker = new WSDLChecker(wsdl);
            logger.info("Verifying wsdl " + wsdl);
            if (checker.check()) {
                servicesWorking.incrementAndGet();
                logger.info("Tracker OK: " + wsdl);
            } else {
                logger.error("Tracker not accessible (enacter " + enacter.getId() + "): " + wsdl);
            }
            return null;
        }
        
    }

}
