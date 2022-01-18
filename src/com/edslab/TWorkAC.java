/**
 * # -- Copyright (c) 2018-2022  Silvio Brandani <support@tcapture.net>. All rights reserved.
 *
 */
package com.edslab;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.postgresql.core.BaseConnection;
import org.postgresql.core.ServerVersion;
import org.postgresql.replication.LogSequenceNumber;
import org.postgresql.replication.PGReplicationStream;

import java.nio.ByteBuffer;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static org.postgresql.replication.LogSequenceNumber.valueOf;


class TWorkAC implements Runnable {
    private AtomicBoolean running = new AtomicBoolean(false);
    private String mythread = "";
    private static final Logger LOGGERWAC= LogManager.getLogger(TWorkAC.class.getName());
    private boolean isException=false;
    private ConnInfo conninfo;
    private ConnInfo rconninfo;
    private PGReplicationStream Cstream ;
    private String walq ;
    private String node;
    private String nodemst;
    private long txidstart;
    private boolean isMaster ;
    private boolean filter ;
    private int workpthread;
    private Queue<Long> queue;
    private int batchLimit;
    private int threadnum;
    private int fatherthreadnum;
    private Map<Long,Set<String>> map_txidlsn;
    private Map<String,String> map_lsndata;
    private Map<String,Long> map_lsnwid;
    private  Map<Long,Set<Integer>> setTxid;
    private Map<Long,Long> map_minlsn_xid;
    private Connection connect;
    private Connection conRdb;
    private Statement st ;
    int mydmlpos;
    long  Txid;
    String scut;

    public TWorkAC(int threadnum, TAStructures tstr, Queue<Long> queueWC) {
        try {

            this.threadnum =threadnum;
            this.nodemst = tstr.nodemst;
            this.fatherthreadnum =tstr.threadn;
            this.isMaster=tstr.isMaster;
            this.node = tstr.node;
            this.walq = "_rdb_bdr.walq__" + tstr.node;
            this.workpthread = tstr.workpthread;
            this.queue = queueWC;
            this.filter = tstr.filter;
            this.txidstart = tstr.txidstart;
            this.batchLimit = 	tstr.batchLimit;
            this.map_txidlsn = 	tstr.map_txidlsn;
            this.map_lsndata = 	tstr.map_lsndata;
            this.map_lsnwid = 	tstr.map_lsnwid;
            this.setTxid = 		tstr.setTxid;
            this.map_minlsn_xid= tstr.map_minlsn_xid;

            /*
                 this.connect = 		tstr.connection;
                 this.conRdb = 		tstr.connectionRdb;
             */
            this.conninfo = tstr.conninfo;
            this.rconninfo = tstr.rconninfo;
        }  catch (Exception ex) {
            LOGGERWAC.error( ex.getMessage());
        }
    }

    public void stopRunning()
    {
        running.set(false);
        LOGGERWAC.info(  mythread+":  Stop Running");
    }

    public void interrupt() {
        running.set(false);
        Thread.currentThread().interrupt();
        LOGGERWAC.info(  mythread+":  Interrupting");
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
        LOGGERWAC.info(  mythread+":  Shutting Down");
    }


    private static String toString(ByteBuffer buffer) {
        int offset = buffer.arrayOffset();
        byte[] source = buffer.array();
        int length = source.length - offset;

        return new String(source, offset, length);
    }


    @Override
    public void run() {
        mythread = "TWorkAC_" + threadnum+ "-"+node;
        Thread.currentThread().setName("TWorkAC_"+ threadnum+ "-"+node );
        LOGGERWAC.info(  mythread+"  is in running state");
        running.set(true);
        int sleepn=0;
        int sleepb=50;
        Long scanxid;
        Long map_minlsn_xidFirst;
        LogSequenceNumber logSN;
        String lastLsn = "";
        long lastXid = 0;
        long lastWid = 0;


        int scanned = 0;
        String qerror;
        boolean managed;

        try {
            createConnection();
            createConnectionRdb();
            st = connect.createStatement();
            connect.setAutoCommit(false);
            Statement str = conRdb.createStatement();
            conRdb.setAutoCommit(false);
            String query="";
            String query_xid="INSERT INTO walq__" + node + "_xid ( xid_from_queue, xid_current,lsn) values  (? , ? , ?::pg_lsn ) ";;
            String query_offset=" update  walq__" + nodemst + "_offset set lsn_offset = ?::pg_lsn , last_offset = ?, xid_offset = ?, local_xid_offset = ?, dateop=now() where src_topic_id ='"+node+"'";
            PreparedStatement  pStat_xid = conRdb.prepareStatement(query_xid);
            PreparedStatement  pStat_offset = conRdb.prepareStatement(query_offset);

            int batchSize=0;
            managed = false;
            long curxid = 0;
            String curlsn ="";


            Queue<Long> unsetTxid = new ConcurrentLinkedQueue<Long>();
            List<TCleanWA> cleanthreads = new ArrayList<>();
            IntStream.range(0, 1).mapToObj(i -> new TCleanWA(i,map_txidlsn , map_lsndata,map_lsnwid,unsetTxid)).forEach(cleanthreads::add);

            for (TCleanWA t : cleanthreads) {
                new Thread(t).start();
            }

            while (running.get()) {
                scanxid = queue.poll();
                if ( scanxid == null) {
                    try {
                        Thread.sleep(100);
                        sleepn++;
                        if (sleepn%sleepb == 0)
                        {
                            LOGGERWAC.trace(  mythread+":sleeping");
                            sleepn = 0;
                        }
                    } catch (InterruptedException e) {
                        LOGGERWAC.trace(mythread+ "Exception" + e.getMessage());
                    }
                } else {
                    managed = false;
                    Set<String> ilsn =  map_txidlsn.get(scanxid);
                    Long getxid = map_minlsn_xid.get(scanxid);
                    LOGGERWAC.trace(mythread +":"+ "---------------------------------------------------------------------------");
                    LOGGERWAC.trace(mythread +":"+ "<Transaction>"+ getxid +"->"+ scanxid+"<Begin>#"+ilsn.size());

                    for (String l : ilsn) {
                            String idata = map_lsndata.get(l);
                            long wid = map_lsnwid.get(l);
                            lastLsn = l;
                            lastWid =wid;
                        try {
                            st.addBatch(idata);
                            LOGGERWAC.trace(mythread + ":" + "<Transaction>" + getxid + "->" + scanxid + "<Batch>" + "idata");
                        } catch (SQLException e) {
                                e.printStackTrace();
                                try {
                                    inCaseOfException();
                                } catch(SQLException sqlee) {
                                    sqlee.printStackTrace();
                                }

                            }
                         catch (Exception e) {
                            isException = true;
                            scanned = -1;
                            // mywid=l;
                            qerror = "insert into _rdb_bdr.walq__" + nodemst + "_conflicts  (wid,xid,schemaf , tablef ,opdml ,state, message ,detail, hint, context)  values ("+wid+", "+ getxid +",'missing' , 'missin' ,'?','error' ,'"+ e.toString().replaceAll("'", "''") + "', '"+ idata.replaceAll("'", "''")+ "','','') ";
                            LOGGERWAC.warn(mythread+":" +"<Exception> into walq__nodemst_conflicts:" + qerror);
                            str.execute(qerror);
                            qerror = "update _rdb_bdr.tc_process set n_operation ='shutdown' , n_dateop= now() where n_pid= " +fatherthreadnum + " and n_mstr = '" +nodemst+ "' and  n_type = 'S';" ;
                            str.execute(qerror);
                            conRdb.commit();
                            e.printStackTrace();
                            break;
                        }

                        /*
                        if ( log_hist && !isException ) {
                            query = "INSERT INTO walq__"+ nodemst +"_log (wid, lsn,xid, data, dateop) VALUES ("+rs2.getLong(1) +",'"+rs2.getString(2)+"',"+rs2.getLong(3)+",'" +rs2.getString(4).replaceAll("'", "''") + "','" +  rs2.getTimestamp (5) + "')";
                            LOGGERWAC.trace(mythread+":" +"query into walq_nodemst_log:" + query);
                            st.addBatch(query);
                        }
                         */

                        if ( !managed  ) {

                            managed = true;
                            PreparedStatement preparedStatement2 = connect.prepareStatement(
                                    (
                                            ((BaseConnection) connect).haveMinimumServerVersion(ServerVersion.v10) ?
                                                    " select case  WHEN isx is null then  txid_current() when isx> 0 then  isx end,pg_current_wal_lsn()::varchar from  txid_current_if_assigned()  as isx;  " :
                                                    " select txid_current(),pg_current_xlog_location()::varchar ;" )
                            );


                            ResultSet rs = preparedStatement2.executeQuery();
                            if (rs.next()) {
                                curxid = rs.getLong(1);
                                curlsn = rs.getString(2);
                            }

                            if (isMaster) {
                                pStat_xid.setLong(1,getxid);
                                pStat_xid.setLong(2,curxid);
                                pStat_xid.setString(3,curlsn);
                                pStat_xid.addBatch();
                                LOGGERWAC.trace(mythread +":"+ "<Transaction>"+ getxid +"<Batch>"+"walq__"+nodemst+"_xid("+curxid + ", " + getxid + ", " +curlsn +")");
                            }

                            pStat_offset.setString(1,l);
                            pStat_offset.setLong(2,wid);
                            pStat_offset.setLong(3,getxid);
                            pStat_offset.setLong(4,scanxid);
                            pStat_offset.addBatch();
                            LOGGERWAC.trace(mythread +":"+ "<Transaction>"+ getxid +"<Batch>"+"walq__"+nodemst+"_offset ("+wid + ", " + l + ", " +getxid+")" );
                        }

                    } // End for ilsn



                    map_minlsn_xidFirst = map_minlsn_xid.keySet().stream().findFirst().get();
                    LOGGERWAC.trace(mythread +":"+ "<Transaction>"+ getxid +"->"+ scanxid+"<Map>map_minlsn_xid<first>"+ map_minlsn_xidFirst );

                    while (scanxid >  map_minlsn_xidFirst ) {
                       try {
                                Thread.sleep(0,1);
                            } catch (InterruptedException e) {
                                LOGGERWAC.trace(mythread+":"+ "Exception" + e.getMessage());
                        }
                        map_minlsn_xidFirst = map_minlsn_xid.keySet().stream().findFirst().get();
                    }


                    if (managed && !isException) {

                        try {
                            st.executeBatch();
                            LOGGERWAC.trace(mythread +":"+ "<Transaction>"+ getxid +"->"+ scanxid+"<executeBatch>");

                        }
                        catch (Exception e) {
                            isException = true;
                            scanned = -1;
                            // mywid=l;
                            qerror = "insert into _rdb_bdr.walq__" + nodemst + "_conflicts  (wid,xid,schemaf , tablef ,opdml ,state, message ,detail, hint, context)  values ("+scanxid+", "+ getxid +",'missing' , 'missin' ,'?','error' ,'"+ e.toString().replaceAll("'", "''") + "', '"+ e.toString().replaceAll("'", "''")+ "','','') ";
                            LOGGERWAC.warn(mythread+":" +"<Exception> into walq__nodemst_conflicts:" + qerror);
                            try {
                                str.execute(qerror);
                                qerror = "update _rdb_bdr.tc_process set n_operation ='shutdown' , n_dateop= now() where n_pid= " + fatherthreadnum + " and n_mstr = '" + nodemst + "' and  n_type = 'S';";
                                str.execute(qerror);
                                conRdb.commit();
                            } catch (SQLException ee) {
                                    ee.printStackTrace();
                                    try {
                                        inCaseOfException();
                                    } catch(SQLException sqlee) {
                                        sqlee.printStackTrace();
                                    }

                                }
                            e.printStackTrace();
                            LOGGERWAC.warn(mythread+":" +"<Exception><Break>" + qerror);
                            break;
                        }


                        try {

                            pStat_offset.setString(1,lastLsn);
                            pStat_offset.setLong(2,lastWid);
                            pStat_offset.setLong(3,getxid);
                            pStat_offset.setLong(4,scanxid);
                            pStat_offset.addBatch();
                            LOGGERWAC.trace(mythread +":"+ "<Transaction>"+ getxid +"<Batch>"+"walq__"+nodemst+"_offset ("+getxid + ", "+ lastWid+")" );

                            pStat_offset.executeBatch();
                            pStat_xid.executeBatch();

                            // LOGGERWAC.trace( mythread+":" +"<Transaction><str> managed executeBatch walq__" + nodemst + "_offset " + getxid + ", " +lastWid);
                            LOGGERWAC.trace(mythread +":"+ "<Transaction>"+ getxid +"<executeBatch>"+"walq__"+nodemst+"_offset ("+getxid + ", "+ lastWid+")" );

                        } catch (SQLException e) {
                            e.printStackTrace();
                            try {
                                inCaseOfException();
                            } catch(SQLException sqlee) {
                                sqlee.printStackTrace();
                            }

                        }


                    }

                    if (managed && !isException) {
                        try {
                            connect.commit();
                            conRdb.commit();

                            // LOGGERWAC.trace(mythread+":" + "<Transaction><connect.commit>  " + scanxid );
                            LOGGERWAC.trace(mythread + ":" + "<Transaction>" + getxid + "->" + scanxid + "<connect.commit>");
                            LOGGERWAC.info(mythread + ":" + "<Transaction>" + getxid + "->" + scanxid + "<End>#" + ilsn.size());

                            //LOGGERWAC.trace( mythread+":" +"<Transaction><conRdb.commit> " + curxid);
                            LOGGERWAC.trace(mythread + ":" + "<Transaction>" + curxid + "<conRdb.commit>");


                            map_minlsn_xid.remove(scanxid);

                            setTxid.remove(scanxid);
                            unsetTxid.add(scanxid);

                        } catch (SQLException ee) {
                            ee.printStackTrace();
                            try {
                                inCaseOfException();
                            } catch(SQLException sqlee) {
                                sqlee.printStackTrace();
                            }

                        }
                    }

                        if (++batchSize >= batchLimit) {
                            LOGGERWAC.trace(mythread + " Send batch with records " + batchSize+  " of " + batchLimit );
                            batchSize=0;
                        }

                } // end else
            }// end while

            for (TCleanWA t : cleanthreads) {
                t.stopRunning();
            }
        }
        catch (SQLException e) {
            e.printStackTrace();
            try {
                inCaseOfException();
            } catch(SQLException sqlee) {
                sqlee.printStackTrace();
            }

        } catch (Exception e) {
            e.printStackTrace();
            LOGGERWAC.trace(mythread + "<Exception><555> " );
            try {
                inCaseOfException();
            } catch(SQLException sqlee) {
                sqlee.printStackTrace();
            }

        }
        finally {
            try {
                if(connect != null)
                    connect.close();

                if(conRdb != null)
                    conRdb.close();
            } catch(SQLException sqlee) {
                sqlee.printStackTrace();
            } finally {  // Just to make sure that both con and stat are "garbage collected"
                connect = null;
                conRdb = null;
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
            conRdb = DriverManager.getConnection(createRdbUrl(), properties);
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

            connect = DriverManager.getConnection(createUrl(), properties);
        } catch (SQLException ex) {
            ex.printStackTrace();
        }

    }

    public void inCaseOfException() throws SQLException {
        String qerror;
        Connection connectionRdbE =null;
        qerror = "update _rdb_bdr.tc_process set n_operation ='shutdown' , n_dateop= now() where n_pid= " +fatherthreadnum + " and n_mstr = '" +nodemst+ "' and  n_type = 'S';" ;
        isException =true;
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

