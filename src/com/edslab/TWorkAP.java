/**
 * # -- Copyright (c) 2018-2022  Silvio Brandani <support@tcapture.net>. All rights reserved.
 *
 */
package com.edslab;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.nio.ByteBuffer;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;

public class TWorkAP implements Runnable {
    private AtomicBoolean running = new AtomicBoolean(false);
    private String mythread = "";
    private static final Logger LOGGERWAP= LogManager.getLogger(TWorkAP.class.getName());
    private Connection connection;
    private Connection connectionRdb;
    private ConnInfo conninfo;
    private ConnInfo rconninfo;
    final static Object obj = new Object();
    private Queue<QueueTrans> queue;
    private int batchLimit;
    private int fatherthreadnum;
    private int threadnum;
    private int workpthread;
    private Map<Long,Set<String>> map_txidlsn;
    private Map<String,String> map_lsndata;
    private Map<String,Long> map_lsnwid;
    private Map<Long,Set<Integer>> setTxid;
    private Map<Long,Long> map_minlsn_xid;
    private String walq;
    private String node;
    private String nodemst = "";
    private boolean filter ;
    private long txidstart;
    int mydmlpos;
    long  Txid;
    String scut;
    long wid;

public TWorkAP(int threadnum, TAStructures tstr, Queue<QueueTrans> queueWP) {
        this.node = tstr.node;
        this.nodemst = tstr.nodemst;
        this.fatherthreadnum =tstr.threadn;
        this.walq = "_rdb_bdr.walq__" + tstr.node;
        this.filter = tstr.filter;
        this.txidstart = tstr.txidstart;
        this.threadnum = threadnum;
        this.batchLimit = tstr.batchLimit;
        this.workpthread = tstr.workpthread;
        this.queue = queueWP;
        this.map_txidlsn = tstr.map_txidlsn;
        this.map_lsndata = tstr.map_lsndata;
        this.map_lsnwid = tstr.map_lsnwid;
        this.setTxid= tstr.setTxid;
        this.map_minlsn_xid= tstr.map_minlsn_xid;
        this.conninfo = tstr.conninfo;
        this.rconninfo = tstr.rconninfo;
    }


    public void stopRunning()
    {
        running.set(false);
        LOGGERWAP.info(  mythread+":  Stop Running");
    }

    public void interrupt() {
        running.set(false);
        Thread.currentThread().interrupt();
        LOGGERWAP.info(  mythread+":  Interrupting");

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
        LOGGERWAP.info(  mythread+":  Shutting Down");
    }


    private static String toString(ByteBuffer buffer) {
        int offset = buffer.arrayOffset();
        byte[] source = buffer.array();
        int length = source.length - offset;

        return new String(source, offset, length);
    }

    public boolean checkFilt(Connection connection, String aquery)
            throws SQLException {
        String v_opdml="";
        String v_schemaf="";
        String v_tablef="";
        String qschma = "";
        String qschema = "";
        String qtable = "";
        String parz = "";

        v_opdml = aquery.substring(0, 1);
        LOGGERWAP.trace( "checkFilt on  " + aquery);
        LOGGERWAP.trace( "v_opdml is  " + v_opdml);

        switch(v_opdml) {
            case "D":
                parz = aquery.substring(12,(aquery.indexOf("WHERE")-1 ));
                LOGGERWAP.trace( "parz is  #" + parz+ "#");
                qschema = parz.substring(0,parz.indexOf("."));
                LOGGERWAP.trace( "qschema is  #" + qschema+"#");
                qtable = parz.substring(parz.indexOf(".")+1);
                LOGGERWAP.trace( "qtable is  #" + qtable+"#");
                break;
            case "I":
                parz = aquery.substring(12,(aquery.indexOf("(")-1 ));
                LOGGERWAP.trace( "parz is  #" + parz+ "#");
                qschema = parz.substring(0,parz.indexOf("."));
                LOGGERWAP.trace( "qschema is  #" + qschema+"#");
                qtable = parz.substring(parz.indexOf(".")+1);
                LOGGERWAP.trace( "qtable is  #" + qtable+"#");
                break;
            case "U":
                parz = aquery.substring(7,(aquery.indexOf("SET")-1 ));
                LOGGERWAP.trace( "parz is  #" + parz+ "#");
                qschema = parz.substring(0,parz.indexOf("."));
                LOGGERWAP.trace( "qschema is  #" + qschema+"#");
                qtable = parz.substring(parz.indexOf(".")+1);
                LOGGERWAP.trace( "qtable is  #" + qtable+"#");
                break;
        }

        try (PreparedStatement preparedStatement = connection.prepareStatement("select 1 from walq__" + node +
                "_filtro where schemaf = ? and(tablef = ? or tablef='all') and strpos(opdml, ? ) > 0 ")) {
            preparedStatement.setString(1, qschema);
            preparedStatement.setString(2, qtable);
            preparedStatement.setString(3, v_opdml);
            try (ResultSet rs = preparedStatement.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }



    @Override
    public void run() {

        mythread = "TWorkAP_"+ threadnum+ "-"+node;
        Thread.currentThread().setName("TWorkAP_"+ threadnum+ "-"+node  );

        LOGGERWAP.info(  mythread+"  is in running state");
        running.set(true);

        QueueTrans w;
        long actXid = 0;
        long actLsn =0;
        long manageXid = -1;
        long iterazioni=0;
        int BoolTxid = -1;
        long Tempxid = 0;
        boolean TxisOpen = false;
        int scanned = 0;
        int sleepn= 0;
        int sleepb= 50;
        Txid = 0;

        try  {
            while (running.get()) {
                w = queue.poll();
                if ( w == null) {
                    try {
                        Thread.sleep(100);
                        sleepn++;
                        if (sleepn%sleepb == 0)
                        {
                            LOGGERWAP.trace(  mythread+":sleeping");
                            sleepn = 0;
                        }
                    } catch (InterruptedException e) {
                    }
                } else {
                    sleepn = 0;
                    String lsn = w.lsn;
                    Long beginLsn = w.beginLsn;
                    Tempxid = w.bXid;
                    scut = w.data;
                    wid = w.wid;

                    LOGGERWAP.trace(mythread +":"+ "<Transaction>"+ Tempxid +"->"+ beginLsn+"<Begin>");
                      if ( (filter && !checkFilt(connection,scut)) ) {
                          LOGGERWAP.trace( mythread+":" + "<Transaction>"+ Tempxid +"<Filter> is " + filter);
                          LOGGERWAP.trace( mythread+":" + "<Transaction>"+ Tempxid +"<Filter> NOT match");
                        } else {
                            Txid = Tempxid;
                            TxisOpen = true;
                            synchronized(obj) { //synchronized block
                                Set<String> x = map_txidlsn.get(beginLsn);
                                if ( x == null ){
                                    x = new ConcurrentSkipListSet<String>();
                                    map_txidlsn.put( beginLsn , x );
                                }
                                x.add(lsn);
                                map_lsndata.put(lsn,scut);
                                map_lsnwid.put(lsn,wid);
                            } // End Sync
                            scanned = scanned + 1;
                          LOGGERWAP.trace(mythread +":"+ "<Transaction>"+ Tempxid +"->"+ beginLsn+"<Map><add><"+ beginLsn+",<lsn>>txidlsn<Size>"+ map_txidlsn.size()+" ");
                          LOGGERWAP.trace(mythread +":"+ "<Transaction>"+ Tempxid +"->"+ beginLsn+"<Map><add><lsn><data>lsndata<Size>"+ map_lsndata.size()+" ");
                          LOGGERWAP.trace(mythread +":"+ "<Transaction>"+ Tempxid +"->"+ beginLsn+"<Map><add><lsn><wid>lsnwid<Size>"+ map_lsnwid.size()+" ");
                        }
                       LOGGERWAP.trace(mythread +":"+ "<Transaction>"+ Tempxid +"->"+ beginLsn+"<Recap><actXid>"+actXid+"<manageXid>"+manageXid+"<actLsn>"+actLsn+"");

                       if ( Txid != actXid  )  {
                            synchronized(obj) { //synchronized block
                            for (Map.Entry<Long, Set <Integer>> entry : setTxid.entrySet()) {
                                if ( entry.getKey() < actLsn ) {
                                    Set<Integer> wrkr = entry.getValue();
                                    if (wrkr.size() < workpthread ) {
                                        wrkr.add(threadnum);
                                    }
                                } else {
                                    break;
                                }
                            }
                        } // End Sync


                        if ( (manageXid !=actXid) && (actXid  > 0)) {
                            synchronized(obj) { //synchronized block
                                Set<Integer> wrk = setTxid.get(actLsn);
                                if ( wrk == null ){
                                    wrk = new TreeSet<Integer>();
                                    setTxid.put( actLsn , wrk );
                                }

                                wrk.add(threadnum);
                            }// End Sync
                            manageXid = actXid;

                        }
                           LOGGERWAP.trace(mythread +":"+ "<Transaction>"+ Tempxid +"->"+ beginLsn+"<Map>setTxid<Size>"+ setTxid.size()+" ");
                        if ((Txid != actXid) ) {
                            actXid = Txid;
                            actLsn = beginLsn;
                        }


                }
                    LOGGERWAP.trace(  mythread   + " -----------------------------------------------------------------------------------------------------------------------------------------");

            }// end else buffer not null

          }

        } catch (SQLException e) {
            e.printStackTrace();
            try {
                inCaseOfException();
            } catch(SQLException sqlee) {
                sqlee.printStackTrace();
            }

        } catch (Exception e) {
            e.printStackTrace();
            try {
                inCaseOfException();
            } catch(SQLException sqlee) {
                sqlee.printStackTrace();
            }

        }  finally {
            try {
                if(connection != null)
                    connection.close();

                if(connectionRdb != null)
                    connectionRdb.close();
            } catch(SQLException sqlee) {
                sqlee.printStackTrace();
            } finally {  // Just to make sure that both con and stat are "garbage collected"
                connection = null;
                connectionRdb = null;
            }
        }

    }

    public void inCaseOfException() throws SQLException {
        String qerror;
        Connection connectionRdbE =null;
        qerror = "update _rdb_bdr.tc_process set n_operation ='shutdown' , n_dateop= now() where n_pid= " +fatherthreadnum + " and n_mstr = '" +nodemst+ "' and  n_type = 'S';" ;

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



    private String createUrl() {
        return "jdbc:postgresql://" + conninfo.host + ':' + conninfo.port + '/' + conninfo.db;
    }


    public void createConnectionRdb() {
        try {
            Properties properties = new Properties();
            properties.setProperty("user", rconninfo.user);
            properties.setProperty("password", rconninfo.pwd);
            properties.setProperty("reWriteBatchedInserts", "true");
            connectionRdb = DriverManager.getConnection(createRdbUrl(), properties);
        } catch (SQLException ex) {
            ex.printStackTrace();
        }

    }

    private String createRdbUrl() {
        // da redirezionare sul rdb rhost rport ruser rpwd quando i walq__nodemst saranno nel rdb__nodeslv
        return "jdbc:postgresql://" + rconninfo.host + ':' + rconninfo.port + '/' + rconninfo.db;
    }


    public void createConnection() {
        try {
            Properties properties = new Properties();
            properties.setProperty("user", conninfo.user);
            properties.setProperty("password", conninfo.pwd);
            properties.setProperty("reWriteBatchedInserts", "true");

            connection = DriverManager.getConnection(createUrl(), properties);
        } catch (SQLException ex) {
            ex.printStackTrace();
        }

    }
}




