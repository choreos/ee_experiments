package org.ow2.choreos.tracker.experiment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;
import org.ow2.choreos.chors.datamodel.Choreography;
import org.ow2.choreos.services.datamodel.DeployableService;
import org.ow2.choreos.services.datamodel.Service;
import org.ow2.choreos.tracker.Enacter;

public class WSDLsVerifier implements Runnable {
    
    private static Logger logger = Logger.getLogger(ChorVerifier.class);
    
    private Enacter enacter;
    private int chorsSize;
    private List<Future<?>> futures = new ArrayList<Future<?>>();
    
    // output
    AtomicInteger servicesWorking = new AtomicInteger();
    long time;

    public WSDLsVerifier(Enacter enacter, int chorsSize) {
        this.enacter = enacter;
        this.chorsSize = chorsSize;
    }

    /**
     * returns the amount of accessible WSDLs
     */
    @Override
    public void run() {
        
        long t0 = System.nanoTime();

        ExecutorService executor = Executors.newFixedThreadPool(chorsSize);
        Choreography chor = enacter.getChoreography();
        int len = chor.getDeployableServices().size();
        logger.info("Verifying " + len + " services in enacter " + enacter.getId());
        for (DeployableService svc : chor.getDeployableServices()) {
            if (svc.getUris() != null && !svc.getUris().isEmpty()) {
                String wsdl = getWsdl(svc);
                CheckerTask task = new CheckerTask(wsdl);
                Future<?> future = executor.submit(task);
                futures.add(future);
            }
        }
        
        logger.info("Waiting for WSDLs verifiers of enacter " + enacter.getId());
        for (Future<?> f : futures) {
            try {
                // we could use wait Concurrency.waitExecutor(executor) here,
                // but it imposes the use of a timeout,
                // and we know that the submitted tasks will be not frozen forever. 
                f.get();
            } catch (InterruptedException e) {
                logger.error("Some WSDL checker of enacter " + enacter.getId() + " failed");
            } catch (ExecutionException e) {
                logger.error("Some WSDL checker of enacter " + enacter.getId() + " failed");
            }
        }
        logger.info("Waiting no more for WSDLs verifiers of enacter" + enacter.getId());
        
        long tf = System.nanoTime();
        time = tf - t0;
    }

    private String getWsdl(Service svc) {
        String uri = svc.getUris().get(0);
        String wsdl = uri.replaceAll("/$", "").concat("?wsdl");
        return wsdl;
    }
    
    private class CheckerTask implements Runnable {
        
        private String wsdl;
        
        public CheckerTask(String wsdl) {
            this.wsdl = wsdl;
        }

        @Override
        public void run() {
            WSDLChecker checker = new WSDLChecker(wsdl);
            if (checker.check()) {
                servicesWorking.incrementAndGet();
                logger.info("Tracker OK: " + wsdl);
            } else {
                logger.error("Tracker not accessible (enacter " + enacter.getId() + "): " + wsdl);
            }
            
        }
        
    }

}
