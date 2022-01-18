/**
 * # -- Copyright (c) 2018-2022  Silvio Brandani <support@tcapture.net>. All rights reserved.
 *
 */
package com.edslab;


import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Queue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.ByteBuffer;
import java.sql.*;
import java.util.Properties;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class TWorkP implements Runnable {
    private AtomicBoolean running = new AtomicBoolean(false);
    private String mythread = "";
    private static final Logger LOGGERWP= LogManager.getLogger(TWorkP.class.getName());
    final static Object obj = new Object();
    private Queue<WalTrans> queue;
    private int batchLimit;
    private int threadnum;
    private int fatherthreadnum;
    private int workpthread;
    private Map<Long,Set<String>> map_txidlsn;
    private Map<String,String> map_lsndata;
    private Map<Long,Set<Integer>> setTxid;
    private Map<Long,Long> map_minlsn_xid;
    private ConnInfo conninfo;
    private ConnInfo rconninfo;
    private Connection connection;
    private Connection connectionRdb;
    private String walq;
    private String node;
    private boolean filter ;
    private long txidstart;

    int mydmlpos;
    long  Txid;
    String scut;



    public TWorkP(int threadnum, TStructures tstr,  Queue<WalTrans> queueWP) {
        this.node = tstr.node;
        this.walq = "_rdb_bdr.walq__" + tstr.node;
        this.filter = tstr.filter;
        this.txidstart = tstr.txidstart;
        this.threadnum = threadnum;
        this.fatherthreadnum =tstr.threadn;
        this.batchLimit = tstr.batchLimit;
        this.workpthread = tstr.workpthread;
        this.queue = queueWP;
        this.map_txidlsn = tstr.map_txidlsn;
        this.map_lsndata = tstr.map_lsndata;
        this.setTxid= tstr.setTxid;
        this.map_minlsn_xid= tstr.map_minlsn_xid;
        this.connection = tstr.connection;
        this.connectionRdb = tstr.connectionRdb;
        this.conninfo = tstr.conninfo;
        this.rconninfo = tstr.rconninfo;
    }


    public void stopRunning()
    {
        running.set(false);
        LOGGERWP.info(  mythread+":  Stop Running");
    }

    public void interrupt() {
        running.set(false);
        Thread.currentThread().interrupt();
        LOGGERWP.info(  mythread+":  Interrupting");

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
        LOGGERWP.info(  mythread+":  Shutting Down");
    }


    private static String toString(ByteBuffer buffer) {
        int offset = buffer.arrayOffset();
        byte[] source = buffer.array();
        int length = source.length - offset;

        return new String(source, offset, length);
    }


    public boolean isXidCurrentinWal(Connection connection, long itxid)
            throws SQLException {
        LOGGERWP.trace( "<< Check >> : select 1 from " + walq + "_xid where xid_current =" + itxid);
        try (PreparedStatement preparedStatement = connection.prepareStatement("select 1 from " + walq + "_xid where xid_current = ?")) {
            preparedStatement.setLong(1, itxid);
            try (ResultSet rs = preparedStatement.executeQuery()) {
                // return rs.next() && rs.getBoolean(1);
                return rs.next();
            }
        }
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
        LOGGERWP.trace( "checkFilt on  " + aquery);
        LOGGERWP.trace( "v_opdml is  " + v_opdml);
        //  schema
        switch(v_opdml) {
            case "D":
                parz = aquery.substring(12,(aquery.indexOf("WHERE")-1 ));
                LOGGERWP.trace( "parz is  #" + parz+ "#");
                qschema = parz.substring(0,parz.indexOf("."));
                LOGGERWP.trace( "qschema is  #" + qschema+"#");
                qtable = parz.substring(parz.indexOf(".")+1);
                LOGGERWP.trace( "qtable is  #" + qtable+"#");
                break;
            case "I":
                parz = aquery.substring(12,(aquery.indexOf("(")-1 ));
                LOGGERWP.trace( "parz is  #" + parz+ "#");
                qschema = parz.substring(0,parz.indexOf("."));
                LOGGERWP.trace( "qschema is  #" + qschema+"#");
                qtable = parz.substring(parz.indexOf(".")+1);
                LOGGERWP.trace( "qtable is  #" + qtable+"#");
                break;
            case "U":
                parz = aquery.substring(7,(aquery.indexOf("SET")-1 ));
                LOGGERWP.trace( "parz is  #" + parz+ "#");
                qschema = parz.substring(0,parz.indexOf("."));
                LOGGERWP.trace( "qschema is  #" + qschema+"#");
                qtable = parz.substring(parz.indexOf(".")+1);
                LOGGERWP.trace( "qtable is  #" + qtable+"#");
                break;
        }
        try (PreparedStatement preparedStatement = connection.prepareStatement("select 1 from " + walq +
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

        mythread = "TWorkP_"+ threadnum+ "-"+node;
        Thread.currentThread().setName("TWorkP_"+ threadnum+ "-"+node  );

        LOGGERWP.info(  mythread+"  is in running state");
        running.set(true);

        WalTrans w;
        long actXid = 0;
        long actLsn =0;
        long manageXid = -1;
        long iterazioni=0;

        long BoolTxid = -1;
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
                            LOGGERWP.trace(  mythread+":sleeping");
                            sleepn = 0;
                        }
                    } catch (InterruptedException e) {

                    }
                } else {
                    sleepn = 0;
                    String s =  toString(w.bb);
                    String lsn = w.lsn.asString();
                    Long beginLsn = w.beginLsn;
                    mydmlpos = s.indexOf("#");
                    Tempxid = Long.parseLong(s.substring(0, mydmlpos));
                    scut = s.substring(mydmlpos + 1, s.length() -1 );
                    LOGGERWP.trace(mythread +":"+ "<Transaction>"+ Tempxid +"->"+ beginLsn+"<Begin>");
                    LOGGERWP.trace(  mythread + "<pre-recap> BoolTxid : "+BoolTxid + " - Tempxid : "+Tempxid+ " - Txid : "+Txid+" - actXid : "+actXid+" - manageXid : "+manageXid+" - beginLsn : "+beginLsn +" - actLsn : "+actLsn);
                    if (Tempxid != BoolTxid && Tempxid != txidstart) {
                        LOGGERWP.trace(  mythread+":" + "<Tempxid> : " + Tempxid + "<line> :" + s  );
                        if (TxisOpen && Txid != Tempxid) {
                            TxisOpen = false;
                        }
                        if ( !TxisOpen &&  isXidCurrentinWal(connectionRdb, Tempxid) ) {
                            /* Imposto BoolTxid per lo skip alla prossima line con txid uguale */
                            BoolTxid = Tempxid;
                            LOGGERWP.trace( mythread+":" + "<Skip > Imposto BoolTxid per lo skip alla prossima line con txid uguale :" + BoolTxid + " txid: " + Tempxid);
                            synchronized(obj) { //synchronized block
                                map_minlsn_xid.remove(beginLsn);
                                LOGGERWP.trace(mythread + "  map_minlsn_xid.remove " + beginLsn);
                            }
                        } else if ( (filter && !checkFilt(connectionRdb,scut)) ) {
                            LOGGERWP.trace( mythread+":" + "<Filter> is " + filter);
                            LOGGERWP.trace( mythread+":" + "<Filter> NOT match");
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
                            } // End Sync
                            scanned = scanned + 1;
                            LOGGERWP.trace(mythread +":"+ "<Transaction>"+ Tempxid +"->"+ beginLsn+"<Map><add><"+ beginLsn+",<lsn>>txidlsn<Size>"+ map_txidlsn.size()+" ");
                            LOGGERWP.trace(mythread +":"+ "<Transaction>"+ Tempxid +"->"+ beginLsn+"<Map><add><lsn><data>lsndata<Size>"+ map_lsndata.size()+" ");

                        } // end else vari check BoolTxid - contains walq -isXidinWal - filters

                    LOGGERWP.trace(mythread +":"+ "<Transaction>"+ Tempxid +"->"+ beginLsn+"<Recap><actXid>"+actXid+"<manageXid>"+manageXid+"<actLsn>"+actLsn+"<boolTxid>"+BoolTxid);
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
                        } // End Sync
                            manageXid = actXid;
                        }
                           LOGGERWP.trace(mythread +":"+ "<Transaction>"+ Tempxid +"->"+ beginLsn+"<Map>setTxid<Size>"+ setTxid.size()+" ");
                        if ((Txid != actXid) ) {
                            actXid = Txid;
                            actLsn = beginLsn;
                        }

                    }
                        LOGGERWP.trace(  mythread   + " -----------------------------------------------------------------------------------------------------------------------------------------");
                 } // end check txid = Booltxid , spostato il 27/10/2021
                    /*
                    else {
                     //   if (Tempxid == BoolTxid) {
                        synchronized(obj) { //synchronized block
                            map_minlsn_xid.remove(beginLsn);
                            LOGGERWP.trace(mythread + "  map_minlsn_xid.remove " + beginLsn);
                        }
                      //  }
                    }

                     */
                } // end else buffer not null

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

        }   finally {
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

    private String createRdbUrl() {
        // da redirezionare sul rdb rhost rport ruser rpwd quando i walq__nodemst saranno nel rdb__nodeslv
        return "jdbc:postgresql://" + rconninfo.host + ':' + rconninfo.port + '/' + rconninfo.db;
    }

    public void inCaseOfException() throws SQLException {
        String qerror;
        Connection connectionRdbE=null;
        qerror = "update _rdb_bdr.tc_process set n_operation ='shutdown' , n_dateop= now() where n_pid= " +fatherthreadnum + " and n_mstr = '" +node+ "' and  n_type = 'M';" ;
      //  qerror = "update _rdb_bdr.tc_process set n_operation ='shutdown' , n_dateop= now() where  n_mstr = '" +node+ "' and  n_type = 'M';" ;
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





