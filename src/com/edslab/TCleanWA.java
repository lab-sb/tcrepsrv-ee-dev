/**
 * # -- Copyright (c) 2018-2022  Silvio Brandani <support@tcapture.net>. All rights reserved.
 *
 */
package com.edslab;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;


class TCleanWA implements Runnable {

  private AtomicBoolean running = new AtomicBoolean(false);
  private String mythread = "";
  private static final Logger LOGGERCL= LogManager.getLogger(TCleanWA.class.getName());
  final static Object obj = new Object();

    private String node ="GC";
    private int threadnum;

   private Map<Long,Set<String>> map_txidlsn;
   private Map<String,String> map_lsndata;
    private Map<String,Long> map_lsnwid;
    private Queue<Long> unsetTxid;


    public TCleanWA(int threadnum, Map<Long,Set<String>> map_txidlsn , Map<String,String> map_lsndata, Map<String,Long> map_lsnwid, Queue<Long> unsetTxid) {
     try {

	this.threadnum =threadnum;
	this.map_txidlsn = 	map_txidlsn;
	this.map_lsndata = 	map_lsndata;
	this.map_lsnwid = 	map_lsnwid;
	this.unsetTxid = 	unsetTxid;
	 }  catch (Exception ex) {
               LOGGERCL.error( ex.getMessage());
        }


    }

    public void stopRunning()
    {
        running.set(false);
        LOGGERCL.info(  mythread+":  Stop Running");
    }

    public void interrupt() {
        running.set(false);
        Thread.currentThread().interrupt();
        LOGGERCL.info(  mythread+":  Interrupting");

    }

   public void stop() {
        running.set(false);
    }

   boolean isRunning() {
        return running.get();
    }

   public String getMyThread() {
        return mythread;
  }

    public void shutDown()
    {
        running.set(false);
        LOGGERCL.info(  mythread+":  Shutting Down");
    }

  public void cleanUp(Long scanxid) {
	 synchronized(obj) { //synchronized block
                        for (Map.Entry<Long, Set <String>> entry : map_txidlsn.entrySet()) {
                               if( entry.getKey() <  scanxid ) {
                                break;
                               } else {
                                       LOGGERCL.info(mythread + " TXid to manage " + entry.getKey());

                                       Set<String> ilsn  = entry.getValue();
                                       for (String l : ilsn) {
                                           map_lsndata.remove(l);
                                           map_lsnwid.remove(l);
                                       }
                                       map_txidlsn.remove(entry.getKey());
                                   }

                        }
                  } // End Sync
 
  }

    @Override
    public void run() {
	mythread = "TCleanWA_" + threadnum+ "-"+node;
        Thread.currentThread().setName("TCleanWA_"+ threadnum+ "-"+node );
        LOGGERCL.info(  mythread+"  is in running state");
	running.set(true);
	Long scanxid;

	 try {
		int sleepn=0;
		int sleepb=10;

	  while (running.get()) {
		scanxid	= unsetTxid.poll(); 
		
		if ( scanxid  == null) {
                    try {

                        Thread.sleep(100);
                         sleepn++;
                        if (sleepn%sleepb == 0)
                        {
                             //    LOGGERCL.trace(  mythread+":sleeping");
                                 sleepn = 0;
                        }
                    } catch (InterruptedException e) {

                    }
		 } else {
			 LOGGERCL.trace(mythread + " TXid to unset " + scanxid);
			Set<String> ilsn =  map_txidlsn.get(scanxid);
			 if ( ilsn == null || ilsn.size() == 0 ) {
                                                return;
                          }
				
			for (String l : ilsn) { 
				  map_lsndata.remove(l);
                 map_lsnwid.remove(l);
			}
			map_txidlsn.remove(scanxid);
			unsetTxid.remove(scanxid);			
		}

	}// end while
	
    } catch(Exception sqlee) {
		   LOGGERCL.trace(mythread+ "Exception" + sqlee.getMessage());
             sqlee.printStackTrace();
    }


  }

}

