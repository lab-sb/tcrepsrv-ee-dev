/**
 * # -- Copyright (c) 2018-2022  Silvio Brandani <support@tcapture.net>. All rights reserved.
 *
 */
package com.edslab;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.postgresql.replication.LogSequenceNumber;
import org.postgresql.replication.PGReplicationStream;
import java.nio.ByteBuffer;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;


class TWorkD implements Runnable {

    private AtomicBoolean running = new AtomicBoolean(false);
    private String mythread = "";
    private static final Logger LOGGERWD= LogManager.getLogger(TWorkD.class.getName());
    private PGReplicationStream Cstream ;
    private String walq ;
    private String node;
    private long txidstart;
    private boolean filter ;
    private int workpthread;
    private int workcthread;
    private int batchLimit;
    private int threadnum;
    private int fatherthreadnum;
    private Map<Long,Set<String>> map_txidlsn;
    private Map<String,String> map_lsndata;
    private  Map<Long,Set<Integer>> setTxid;
    private Map<Long,Long> map_minlsn_xid;
    private ConnInfo conninfo;
    private ConnInfo rconninfo;
    private Connection connect;
    private Connection con;
    private Statement st ;
    int mydmlpos;
    long  Txid;
    String scut;
    private List<Queue<Long>> queue = new ArrayList<>();

    public TWorkD(int threadnum, TStructures tstr,List<Queue<Long>> queueWC) {
        try {
            this.Cstream = tstr.stream;
            this.threadnum =threadnum;
            this.node = tstr.node;
            this.fatherthreadnum =tstr.threadn;
            this.walq = "_rdb_bdr.walq__" + tstr.node;
            this.workpthread = tstr.workpthread;
            this.workcthread = tstr.workcthread;
            this.filter = tstr.filter;
            this.txidstart = tstr.txidstart;
            this.batchLimit = 	tstr.batchLimit;
            this.queue = 		queueWC;
            this.map_txidlsn = 	tstr.map_txidlsn;
            this.map_lsndata = 	tstr.map_lsndata;
            this.setTxid = 		tstr.setTxid;
            this.map_minlsn_xid= tstr.map_minlsn_xid;
            this.connect = 		tstr.connection;
            this.con = 		tstr.connectionRdb;
            this.conninfo = tstr.conninfo;
            this.rconninfo = tstr.rconninfo;
        }  catch (Exception ex) {
            LOGGERWD.error( ex.getMessage());
        }


    }

    public void stopRunning()
    {
        running.set(false);
        LOGGERWD.info(  mythread+":  Stop Running");
    }

    public void interrupt() {
        running.set(false);
        Thread.currentThread().interrupt();
        LOGGERWD.info(  mythread+":  Interrupting");

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
        LOGGERWD.info(  mythread+":  Shutting Down");
    }


    private static String toString(ByteBuffer buffer) {
        int offset = buffer.arrayOffset();
        byte[] source = buffer.array();
        int length = source.length - offset;

        return new String(source, offset, length);
    }


    @Override
    public void run() {
        mythread = "TWorkD_" + threadnum+ "-"+node;
        Thread.currentThread().setName("TWorkD_"+ threadnum+ "-"+node );

        LOGGERWD.info(  mythread+"  is in running state");
        running.set(true);
        int sleepn=0;
        int sleepb=50;
        int wpx = 0;
        int wpmax = workpthread;
        int wcmax = workcthread;
        LogSequenceNumber logSN;
        String lastLsn = "";
        try {
            int batchSize=0;
            Queue<Long> unsetTxid = new ConcurrentLinkedQueue<Long>();
            List<TCleanW> cleanthreads = new ArrayList<>();
            IntStream.range(0, 1).mapToObj(i -> new TCleanW(i,map_txidlsn , map_lsndata,unsetTxid)).forEach(cleanthreads::add);

            for (TCleanW t : cleanthreads) {
                new Thread(t).start();
            }

            while (running.get()) {

                if (setTxid.isEmpty() ) {
                    try {
                        //LOGGERWC.trace(  mythread+" sleeping for empty setTxid");
                        Thread.sleep(100);
                        sleepn++;
                        if (sleepn%sleepb == 0)
                        {
                            LOGGERWD.trace(  mythread+":sleeping");
                            sleepn = 0;
                        }

                    } catch (InterruptedException e) {
                        LOGGERWD.trace(mythread+ "Exception" + e.getMessage());

                    }
                } else {

                    Long scanxid = setTxid.keySet().stream().findFirst().get();
                    Set<Integer> iwrk = setTxid.get(scanxid);

                    if (  iwrk != null && iwrk.size() == workpthread ) {
                        wpx++;
                        if (wpx%wcmax == 0) wpx = 0;
                        LOGGERWD.trace(mythread + ":" + "<Queue><add><TWorkC_" + wpx + "<Queue><Size>" + queue.get(wpx).size());
                        queue.get(wpx).add(scanxid);
                        setTxid.remove(scanxid);


                    } // if size < workpthread
                     else {
                        Thread.sleep(100);
                    }



                } // end else

            }// end while


            for (TCleanW t : cleanthreads) {
                t.stopRunning();
                //t.stop();
            }

        }

        catch (Exception e) {
            e.printStackTrace();
            try {
                inCaseOfException();
            } catch(SQLException sqlee) {
                sqlee.printStackTrace();
            }

        }


    }

    private String createRdbUrl() {
        return "jdbc:postgresql://" + rconninfo.host + ':' + rconninfo.port + '/' + rconninfo.db;
    }

    public void inCaseOfException() throws SQLException {
        String qerror;
        Connection connectionRdbE =null;
//        qerror = "update _rdb_bdr.tc_process set n_operation ='shutdown' , n_dateop= now() where  n_mstr = '" +node+ "' and  n_type = 'M';" ;
        qerror = "update _rdb_bdr.tc_process set n_operation ='shutdown' , n_dateop= now() where n_pid= " +fatherthreadnum + " and n_mstr = '" +node+ "' and  n_type = 'M';" ;

        try {
            Properties properties = new Properties();
            properties.setProperty("user", rconninfo.user);
            properties.setProperty("password", rconninfo.pwd);
            properties.setProperty("reWriteBatchedInserts", "true");

            connectionRdbE = DriverManager.getConnection(createRdbUrl(), properties);
            Statement str = connectionRdbE.createStatement();
            str.execute(qerror);
        } catch (SQLException ex) {
            ex.printStackTrace();
        }  finally {
            try {

                if(connectionRdbE != null)
                    connectionRdbE.close();
            } catch(SQLException sqlee) {
                sqlee.printStackTrace();
            } finally {  // Just to make sure that both con and stat are "garbage collected"
                connectionRdbE = null;
            }
        }

    }

}

