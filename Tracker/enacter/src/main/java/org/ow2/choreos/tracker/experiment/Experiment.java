package org.ow2.choreos.tracker.experiment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.ow2.choreos.tracker.Enacter;
import org.ow2.choreos.utils.Concurrency;
import org.ow2.choreos.utils.LogConfigurator;


public class Experiment {

    public static final int RUN = Integer.parseInt(ExperimentConfiguration.get("RUN"));
    public static final int CHORS_SIZE = Integer.parseInt(ExperimentConfiguration.get("CHORS_SIZE"));
    public static final int VM_LIMIT = Integer.parseInt(ExperimentConfiguration.get("VM_LIMIT"));
    public static final int CHORS_QTY = Integer.parseInt(ExperimentConfiguration.get("CHORS_QTY"));

    public static final int ENACTMENT_TIMEOUT = 50;
    public static final int VERIFY_CHORS_TIMEOUT = 5;
    public static final int VERIFY_WSDLS_TIMEOUT = 10;
    
    private int run, chorsQty, chorsSize, vmLimit;
    private Report report;
    private List<RunnableEnacter> enacters;
    private Map<Integer, ChorVerifier> chorVerifiers;
    private Map<Integer, WSDLsVerifier> wsdlVerifiers;
    
    private static Logger logger = Logger.getLogger(Experiment.class);

    public static void main(String[] args) {
        LogConfigurator.configLog();
        Experiment experiment = new Experiment(RUN, CHORS_QTY, CHORS_SIZE, VM_LIMIT);
        experiment.run();
    }

    public Experiment(int run, int chorsQty, int chorsSize, int vmLimit) {
        this.run = run;
        this.chorsQty = chorsQty;
        this.chorsSize = chorsSize;
        this.vmLimit = vmLimit;
    }
    
    public Experiment(ExperimentDefinition def) {
        this.run = def.getRun();
        this.chorsQty = def.getChorsQty();
        this.chorsSize = def.getChorsSize();
        this.vmLimit = def.getVmLimit();
    }

    public void run() {

        report = new Report(run, chorsQty, chorsSize, vmLimit);
        logger.info("Running " + report.header);

        long t0_total = System.nanoTime();

        enactTrackers();
        verifyChors();
        verifyWSDLs();
        
        long tf_total = System.nanoTime();
        long delta_total = tf_total - t0_total;
        report.setTotalTime(delta_total);

        finishReport();

        System.out.println(report);
        try {
            report.toFile();
        } catch (IOException e) {
            logger.error("Could not save the report.");
        }
    }

    private void enactTrackers() {
        ExecutorService executor = Executors.newFixedThreadPool(chorsQty);
        enacters = new ArrayList<RunnableEnacter>();
        long t0 = System.nanoTime();
        for (int i = 0; i < chorsQty; i++) {
            Enacter enacter = new Enacter(i);
            RunnableEnacter runnable = new RunnableEnacter(enacter, report, chorsSize);
            enacters.add(runnable);
            executor.submit(runnable);
        }

        Concurrency.waitExecutor(executor, ENACTMENT_TIMEOUT, "Could not properly enact all the chors");
        long tf = System.nanoTime();
        report.setChorsEnactmentTotalTime(tf - t0);
    }
    
    private void verifyChors() {

        ExecutorService executor = Executors.newFixedThreadPool(chorsQty);
        chorVerifiers = new HashMap<Integer, ChorVerifier>();
        long t0 = System.nanoTime();
        for (RunnableEnacter enacter : enacters) {
            ChorVerifier verifier = new ChorVerifier(enacter.enacter);
            chorVerifiers.put(enacter.enacter.getId(), verifier);
            executor.submit(verifier);
        }
        
        logger.info("Waiting for enacters verifiers");
        Concurrency.waitExecutor(executor, VERIFY_CHORS_TIMEOUT, TimeUnit.MINUTES, logger, "Could not properly verify all the chors");
        logger.info("Waiting no more for enacters verifiers");
        long tf = System.nanoTime();
        report.setCheckTotalTime(tf - t0);
    }
    
    private void verifyWSDLs() {
        
        ExecutorService executor = Executors.newFixedThreadPool(chorsQty);
        wsdlVerifiers = new HashMap<Integer, WSDLsVerifier>();
        long t0 = System.nanoTime();
        for (RunnableEnacter enacter : enacters) {
            WSDLsVerifier verifier = new WSDLsVerifier(enacter.enacter, chorsQty);
            wsdlVerifiers.put(enacter.enacter.getId(), verifier);
            executor.submit(verifier);
        }
        
        logger.info("Waiting for WSDLs verifiers");
        Concurrency.waitExecutor(executor, VERIFY_WSDLS_TIMEOUT, TimeUnit.MINUTES, logger, "Could not properly verify all the chors");
        logger.info("Waiting no more for WSDLs verifiers");
        long tf = System.nanoTime();
        report.setCheckTotalTime(tf - t0);
    }
    
    private void finishReport() {
        int chorsWorking = 0;
        int servicesWorking = 0;
        for (int id : chorVerifiers.keySet()) {
            ChorVerifier chorVerifier = chorVerifiers.get(id);
            long checkTime = chorVerifier.time;
            if (chorVerifier.ok) {
                chorsWorking++;
                servicesWorking += chorsSize;
            } else {
                WSDLsVerifier wsdlsVerifier = wsdlVerifiers.get(id);
                servicesWorking += wsdlsVerifier.servicesWorking.get();
                checkTime += wsdlsVerifier.time;
            }
            report.addCheckTime(checkTime);
        }
        report.setChorsWorking(chorsWorking);
        report.setServicesWorking(servicesWorking);
    }

}
