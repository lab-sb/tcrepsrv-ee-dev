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
import static org.postgresql.replication.LogSequenceNumber.valueOf;

class TWorkC implements Runnable {
    final static Object obj = new Object();
    private AtomicBoolean running = new AtomicBoolean(false);
    private String mythread = "";
    private static final Logger LOGGERWC= LogManager.getLogger(TWorkC.class.getName());
    private ConnInfo conninfo;
    private ConnInfo rconninfo;
    private PGReplicationStream Cstream ;
    private String walq ;
    private String node;
    private long txidstart;
    private boolean filter ;
    private int workpthread;
    private Queue<Long> queue;
    private int batchLimit;
    private int threadnum;
    private int fatherthreadnum;
    private Map<Long,Set<String>> map_txidlsn;
    private Map<String,String> map_lsndata;
    private  Map<Long,Set<Integer>> setTxid;
    private Map<Long,Long> map_minlsn_xid;
    private Connection connect;
    private Connection con;
    private Statement st ;
    int mydmlpos;
    long  Txid;
    String scut;

    public TWorkC(int threadnum, TStructures tstr,Queue<Long> queueWC) {
        try {
            this.Cstream = tstr.stream;
            this.threadnum =threadnum;
            this.node = tstr.node;
            this.fatherthreadnum =tstr.threadn;
            this.walq = "_rdb_bdr.walq__" + tstr.node;
            this.workpthread = tstr.workpthread;
            this.queue = queueWC;
            this.filter = tstr.filter;
            this.txidstart = tstr.txidstart;
            this.batchLimit = 	tstr.batchLimit;
            this.map_txidlsn = 	tstr.map_txidlsn;
            this.map_lsndata = 	tstr.map_lsndata;
            this.setTxid = 		tstr.setTxid;
            this.map_minlsn_xid= tstr.map_minlsn_xid;
            /*
                  this.connect = 		tstr.connection;
                    this.con = 		tstr.connectionRdb;
             */
            this.conninfo = tstr.conninfo;
            this.rconninfo = tstr.rconninfo;

        }  catch (Exception ex) {
            LOGGERWC.error( ex.getMessage());
        }


    }

    public void stopRunning()
    {
        running.set(false);
        LOGGERWC.info(  mythread+":  Stop Running");
    }

    public void interrupt() {
        running.set(false);
        Thread.currentThread().interrupt();
        LOGGERWC.info(  mythread+":  Interrupting");

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
        LOGGERWC.info(  mythread+":  Shutting Down");
    }

    public void createConnectionRdb() {
        try {
            Properties properties = new Properties();
            properties.setProperty("user", rconninfo.user);
            properties.setProperty("password", rconninfo.pwd);
            properties.setProperty("reWriteBatchedInserts", "true");
            con = DriverManager.getConnection(createRdbUrl(), properties);
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private String createRdbUrl() {
        // da redirezionare sul rdb rhost rport ruser rpwd quando i walq__nodemst saranno nel rdb__nodeslv
        return "jdbc:postgresql://" + rconninfo.host + ':' + rconninfo.port + '/' + rconninfo.db;
    }

    private String createUrl() {
        return "jdbc:postgresql://" + conninfo.host + ':' + conninfo.port + '/' + conninfo.db;
    }

    public void createConnection() {
        try {
            Properties properties = new Properties();
            properties.setProperty("user", conninfo.user);
            properties.setProperty("password", conninfo.pwd);
            properties.setProperty("reWriteBatchedInserts", "true");

            connect = DriverManager.getConnection(createUrl(), properties);
        } catch (SQLException ex) {
            ex.printStackTrace();
        }

    }

    private static String toString(ByteBuffer buffer) {
        int offset = buffer.arrayOffset();
        byte[] source = buffer.array();
        int length = source.length - offset;

        return new String(source, offset, length);
    }


    @Override
    public void run() {
        mythread = "TWorkC_" + threadnum+ "-"+node;
        Thread.currentThread().setName("TWorkC_"+ threadnum+ "-"+node );
        LOGGERWC.info(  mythread+"  is in running state");
        running.set(true);
        int sleepn=0;
        int sleepb=50;
        Long scanxid;
        Long map_minlsn_xidFirst;
        LogSequenceNumber logSN;
        String lastLsn = "";
        try {
            createConnectionRdb();

            st = con.createStatement();


            con.setAutoCommit(false);
            String query = null;
            /*

            String query = "insert into " + walq + " (wid,lsn,xid,current_xid,data,dateop,local_xid) values  ( nextval('" + walq + "_wid_seq'::regclass),?::pg_lsn, ?, txid_current(),?,pg_xact_commit_timestamp( ?::xid ) , ?)";
            PreparedStatement  preparedStatement = con.prepareStatement(query);

             */

            int batchSize=0;

            Queue<Long> unsetTxid = new ConcurrentLinkedQueue<Long>();
            List<TCleanW> cleanthreads = new ArrayList<>();
            IntStream.range(0, 1).mapToObj(i -> new TCleanW(i,map_txidlsn , map_lsndata,unsetTxid)).forEach(cleanthreads::add);

            for (TCleanW t : cleanthreads) {
                new Thread(t).start();
            }

            while (running.get()) {
                scanxid = queue.poll();
                if ( scanxid == null) {
                    try {
                        Thread.sleep(100);
                       // LOGGERWC.trace(  mythread+":sleeping ");
                        sleepn++;
                        if (sleepn%sleepb == 0)
                        {
                            LOGGERWC.trace(  mythread+":sleeping "+sleepb );
                            sleepn = 0;
                        }

                    } catch (InterruptedException e) {
                        LOGGERWC.trace(mythread+ "Exception" + e.getMessage());

                    }
                } else {
                    Set<String> ilsn =  map_txidlsn.get(scanxid);
                    Long getxid = map_minlsn_xid.get(scanxid);
                    LOGGERWC.trace(mythread +":"+ "---------------------------------------------------------------------------");
                    LOGGERWC.trace(mythread +":"+ "<Transaction>"+ getxid +"->"+ scanxid+"<Begin>#"+ilsn.size());
                    for (String l : ilsn) {
                            String idata = map_lsndata.get(l);

                 /*
                 //  query = "insert into " + walq + " (wid,lsn,xid,current_xid,data) values  ( nextval('" + walq + "_wid_seq'::regclass),'" + l + "', " + getxid+ ", txid_current(),'" + idata.replaceAll("'", "''") + "--" + node + "')";
                     query = "insert into " + walq + " (wid,lsn,xid,current_xid,data,dateop) values  ( nextval('" + walq + "_wid_seq'::regclass),'" + l + "', " + getxid+ ", txid_current(),'" + idata.replaceAll("'", "''") + "--" + node + "' ,pg_xact_commit_timestamp('"+getxid+"') )";
                 //  query = "insert into " + walq + " (lsn,xid,current_xid,data) values  ( '" + l + "', " + getxid+ ", txid_current(),'" + idata.replaceAll("'", "''") + "--" + node + "')";

                            preparedStatement.setString (1,l); // LogSequenceNumber.valueOf(lsn);
                            preparedStatement.setLong(2,getxid);
                            preparedStatement.setString(3,idata + "--" + node);
                            preparedStatement.setString(4, getxid.toString());
                            preparedStatement.setLong(5,scanxid);
                            preparedStatement.addBatch();

                  */
                          LOGGERWC.trace(mythread + ":" + "<Transaction>" + getxid + "->" + scanxid + "<Batch>" + "idata");
                          query = "insert into " + walq + " (wid,lsn,xid,current_xid,data,dateop,local_xid) values  ( nextval('" + walq + "_wid_seq'::regclass),'" + l + "', " + getxid+ ", txid_current(),'" + idata.replaceAll("'", "''") + "--" + node + "' ,pg_xact_commit_timestamp('"+getxid+"'),"+scanxid+" )";

                           st.addBatch(query);

                            lastLsn = l;
                        }
                    LOGGERWC.trace(mythread + ":" + "<Transaction>" + getxid + "->" + scanxid +" End loop - st.addBatch ");

                    st.executeBatch();


                    /*
                            preparedStatement.executeBatch();
                    */

                    LOGGERWC.trace(mythread +":"+ "<Transaction>"+ getxid +"->"+ scanxid+"<executeBatch>");

                    map_minlsn_xidFirst = map_minlsn_xid.keySet().stream().findFirst().get();
                    LOGGERWC.trace(mythread +":"+ "<Transaction>"+ getxid +"->"+ scanxid+"<Map>map_minlsn_xid<first>"+ map_minlsn_xidFirst );

                    while (scanxid >  map_minlsn_xidFirst ) {
                        //LOGGERWC.trace(mythread+ "map_minlsn_xidFirst" +map_minlsn_xidFirst + "scanxid"+scanxid);
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException e) {
                            LOGGERWC.trace(mythread+ "Exception" + e.getMessage());
                        }
                        map_minlsn_xidFirst = map_minlsn_xid.keySet().stream().findFirst().get();
                    }
                     con.commit();

                    LOGGERWC.trace(mythread +":"+ "<Transaction>"+ getxid +"->"+ scanxid+"<connect.commit>");
                    LOGGERWC.info(mythread +":"+ "<Transaction>"+ getxid +"->"+ scanxid+"<End>#"+ilsn.size());
                    synchronized(obj) { //synchronized block
                        map_minlsn_xid.remove(scanxid);
                        setTxid.remove(scanxid);
                        unsetTxid.add(scanxid);
                    }
                         logSN = valueOf(lastLsn);

                        Cstream.setAppliedLSN(logSN);
                        Cstream.setFlushedLSN(logSN);
                        LOGGERWC.trace(mythread + " setAppliedLSN/setFlushedLSN to "+logSN);


                        if (++batchSize >= batchLimit) {
                            LOGGERWC.trace(mythread + " Send batch with records " + batchSize+  " of " + batchLimit );
                            batchSize=0;
                        }
                } // end else
            }// end while

            for (TCleanW t : cleanthreads) {
                t.stopRunning();
            }

        }

        catch (Exception e) {
            e.printStackTrace();
            try {
                inCaseOfException();
            } catch(SQLException sqlee) {
                sqlee.printStackTrace();
            }

        } finally {
            try {
                if(con != null)
                    con.close();

                if(connect != null)
                    connect.close();
            } catch(SQLException sqlee) {
                sqlee.printStackTrace();
            } finally {  // Just to make sure that both con and stat are "garbage collected"
                con = null;
                connect = null;
            }
        }


    }


    public void inCaseOfException() throws SQLException {
        String qerror;
        Connection connectionRdbE=null;
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

