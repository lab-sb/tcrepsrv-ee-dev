/**
 * # -- Copyright (c) 2018-2022  Silvio Brandani <support@tcapture.net>. All rights reserved.
 *
 */
package com.edslab;

import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.postgresql.PGConnection;
import org.postgresql.PGProperty;
import org.postgresql.core.BaseConnection;
import org.postgresql.core.ServerVersion;
import org.postgresql.replication.LogSequenceNumber;
import org.postgresql.replication.PGReplicationStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

/**
 * Logical Decoding TCapt
 */
public class TCapt implements Runnable {
    private AtomicBoolean running = new AtomicBoolean(false);
    private String mythread = "";
    private static final Logger LOGGERT= LogManager.getLogger(TCapt.class.getName());
    private Connection connection;
    private Connection connectionRdb;
    private Connection replicationConnection;
    private String nodemst = "";
     private int threadn = 0;
    private String host = "";
    private String user = "";
    private String pwd = "";
    private int port = 5432;
    private String db = "";
    private String node = "";
    private String walq = "";

    private String rhost = "";
    private String ruser = "";
    private String rpwd = "";
    int rport = 5432;
    private String rdb = "";
    int walqtrunc = 9;
    int batch_size = 1000;

    int workpthread = 2;
    int workcthread =2;
    int xidmax = 10;
    int maplimit =10000;

    private boolean filter = false;
    public boolean isInitialized = true;

    private String currentlsnfunc = "";

    private static String toString(ByteBuffer buffer) {
        int offset = buffer.arrayOffset();
        byte[] source = buffer.array();
        int length = source.length - offset;

        return new String(source, offset, length);
    }

    private String createUrl() {
        return "jdbc:postgresql://" + host + ':' + port + '/' + db;
    }

    private String createRdbUrl() {
        return "jdbc:postgresql://" + rhost + ':' + rport + '/' + rdb;
    }

    public void createConnectionRdb() {
        try {
            Properties properties = new Properties();
            properties.setProperty("user", ruser);
            properties.setProperty("password", rpwd);
            properties.setProperty("reWriteBatchedInserts", "true");
            connectionRdb = DriverManager.getConnection(createRdbUrl(), properties);
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    public void createConnection() {
        try {
            Properties properties = new Properties();
            properties.setProperty("user", user);
            properties.setProperty("password", pwd);
            properties.setProperty("reWriteBatchedInserts", "true");
            connection = DriverManager.getConnection(createUrl(), properties);
        } catch (SQLException ex) {
            ex.printStackTrace();
        }

    }


    public TCapt(String[] args) {
	try {
		loadProps(args);
      	}  catch (Exception ex) {
               LOGGERT.error( ex.getMessage());
        }
     }

    public void stopRunning()
    {
	running.set(false);
	LOGGERT.info(  mythread+":  Stop Running");
    }
	
    public void interrupt() {
        running.set(false);
        Thread.currentThread().interrupt();
	LOGGERT.info(  mythread+":  Interrupting");

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
        LOGGERT.info(  mythread+":  Shutting Down");
    }


 public void inCaseOfException() throws SQLException {
 String qerror;
  Connection connectionRdbE =null;
  qerror = "update _rdb_bdr.tc_process set n_operation ='shutdown' , n_dateop= now() where n_pid= " +threadn + " and n_mstr = '" +nodemst+ "' and  n_type = 'M';" ;
  //   qerror = "update _rdb_bdr.tc_process set n_operation ='shutdown' , n_dateop= now() where  n_mstr = '" +nodemst+ "' and  n_type = 'M';" ;
     try {
         Properties properties = new Properties();
         properties.setProperty("user", ruser);
         properties.setProperty("password", rpwd);
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
  

    public void run()  {
	mythread = "TC-"+ nodemst  +"_"+ threadn;;
	Thread.currentThread().setName("TC-"+ nodemst );
   
        LOGGERT.info(  mythread+":  is in running state");

	running.set(true);
        createConnection();
        createConnectionRdb();
        try {
            openReplicationConnection();
            currentlsnfunc= setCurrentLSNFunction();

            LOGGERT.trace(  mythread+":  set currentlsn:"+currentlsnfunc);

  	    receiveChangesOccursBeforTCapt();
	 } catch (InterruptedException e) {
            e.printStackTrace();
                 try {
                        inCaseOfException();
                } catch(SQLException sqlee) {
                        sqlee.printStackTrace();
                }

        } catch (SQLException e) {
            e.printStackTrace();
                 try {
                        inCaseOfException();
                } catch(SQLException sqlee) {
                        sqlee.printStackTrace();
                }

        } catch (TimeoutException e) {
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

        }
		finally {
    	       	try {
        		if(connection != null)
            			connection.close();
			if(connectionRdb != null)
                                connectionRdb.close();
			if(replicationConnection!= null)
                                replicationConnection.close();
    			} catch(SQLException sqlee) {
        			sqlee.printStackTrace();
    			} finally {  // Just to make sure that both con and stat are "garbage collected"
        			connection = null;
        			connectionRdb = null;
        			replicationConnection = null;
    			}
		}
  }

    public boolean isXidDateopGreater(Connection connectionRdb, long itxid, Timestamp qdateop )
            throws SQLException {
        Statement statement =  connectionRdb.createStatement();
        LOGGERT.trace(  mythread+":" + "<Check> : " + itxid + " has date greater than qdateop :" + qdateop);
        try  {
            ResultSet rs = statement.executeQuery("select pg_xact_commit_timestamp ('" + itxid+ "') >  '" + qdateop +"'");
                 return rs.next() && rs.getBoolean(1);
        } catch ( SQLException e ) {
            e.printStackTrace();
        }
        return true;
    }

    public boolean isXidCurrentinWal(Connection connectionRdb, long itxid)
            throws SQLException {
        LOGGERT.trace(  mythread+":" + "<< Check >> : select 1 from " + walq + "_xid where xid_current =" + itxid);
        try (PreparedStatement preparedStatement = connectionRdb.prepareStatement("select 1 from " + walq + "_xid where xid_current = ?")) {
            preparedStatement.setLong(1, itxid);
            try (ResultSet rs = preparedStatement.executeQuery()) {
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
        //SELECT  substring(rlcMena.data,1,1) into v_opdml;
        LOGGERT.trace( mythread+":" +  "checkFilt on  " + aquery);
        LOGGERT.trace(  mythread+":" + "v_opdml is  " + v_opdml);
        //  schema
        switch(v_opdml) {
            case "D":
                parz = aquery.substring(12,(aquery.indexOf("WHERE")-1 ));
                LOGGERT.trace(  mythread+":" + "parz is  #" + parz+ "#");
                qschema = parz.substring(0,parz.indexOf("."));
                LOGGERT.trace(  mythread+":" + "qschema is  #" + qschema+"#");
                qtable = parz.substring(parz.indexOf(".")+1);
                LOGGERT.trace(  mythread+":" + "qtable is  #" + qtable+"#");
                break;
            case "I":
                parz = aquery.substring(12,(aquery.indexOf("(")-1 ));
                LOGGERT.trace(  mythread+":" + "parz is  #" + parz+ "#");
                qschema = parz.substring(0,parz.indexOf("."));
                LOGGERT.trace(  mythread+":" + "qschema is  #" + qschema+"#");
                qtable = parz.substring(parz.indexOf(".")+1);
                LOGGERT.trace(  mythread+":" + "qtable is  #" + qtable+"#");
                break;
            case "U":
                parz = aquery.substring(7,(aquery.indexOf("SET")-1 ));
                LOGGERT.trace(  mythread+":" + "parz is  #" + parz+ "#");
                qschema = parz.substring(0,parz.indexOf("."));
                LOGGERT.trace(  mythread+":" + "qschema is  #" + qschema+"#");
                qtable = parz.substring(parz.indexOf(".")+1);
                LOGGERT.trace(  mythread+":" + "qtable is  #" + qtable+"#");
                break;
        }

        try (PreparedStatement preparedStatement = connection.prepareStatement("select 1 from " + walq +
                "_filtro where schemaf = ? and(tablef = ? or tablef='all') and strpos(opdml, ? ) > 0   UNION " +
                "Select 1 from " + walq +"_filtro where '__events_ddl' =  ? and 'dba' =? ")) {
            preparedStatement.setString(1, qschema);
            preparedStatement.setString(2, qtable);
            preparedStatement.setString(3, v_opdml);
            preparedStatement.setString(4, qtable);
            preparedStatement.setString(5, qschema);
            try (ResultSet rs = preparedStatement.executeQuery()) {
                 return rs.next() && rs.getBoolean(1);
            }
        }
    }


    public void receiveChangesOccursBeforTCapt() throws Exception {
        PGConnection pgConnection = (PGConnection) replicationConnection;
        String querychk;
        long txidstart = 0;
        long qlocal_xid= 0;
        Timestamp qdateop =null;
        long qwid;
        String mylsn;
        String qlsn;
        int mydmlpos;
        int scanned;

        StringBuilder sb = new StringBuilder();
        String RdbSlotBdr = "rdb_" + node + "_bdr";
        LogSequenceNumber lsn = null;
        LogSequenceNumber slotlsn = null;
        Statement stp = connection.createStatement();
        connectionRdb.setAutoCommit(true);
        Statement st = connectionRdb.createStatement();

    /*
		TWorks Structures
	*/

        List<TWorkP> threads = new ArrayList<>();
        List<TWorkC> consthreads = new ArrayList<>();
        List<TWorkD> disphreads = new ArrayList<>();
        Queue<WalTrans> queue = new ConcurrentLinkedQueue<WalTrans>();
        Map<Long, Set<String>> map_txidlsn = new ConcurrentHashMap<Long,Set<String>>();
        Map<String,String> map_lsndata = new ConcurrentHashMap<String,String>();
        Map<Long,Set<Integer>> setTxid = new ConcurrentSkipListMap<Long,Set<Integer>>();
        Map<Long,Long> map_minlsn_xid = new ConcurrentSkipListMap<Long,Long>();
        int BATCH_SIZE = 500;
        List<Queue<WalTrans>> queueWP = new ArrayList<>();
        List<Queue<Long>> queueWC = new ArrayList<>();
        long lastxid = 0;
        long tobeassignedxid = 0;
        long beginLsn;
        int wpmax = workpthread;
        int wpx = 0;
        int xidn = 0;
        boolean first;

	try {

        slotlsn = getSlotLSN(RdbSlotBdr);
        LogSequenceNumber currentLSN = getCurrentLSN();

        LOGGERT.trace(  mythread+":" + "<Slot> " + RdbSlotBdr + " <getSlotLSN> " + slotlsn + " <getCurrentLSN> " + currentLSN );
        querychk = "select lsn::varchar,xid,wid,dateop, local_xid from " + walq + " where wid = (select max(wid) from " + walq + "   where local_xid = (select max(local_xid)   from  " + walq + " ))";
        ResultSet rs = st.executeQuery(querychk);
        if (rs.next()) {
            String slsn = rs.getString(1);
            lsn = LogSequenceNumber.valueOf(slsn);
            txidstart = rs.getLong(2);
            qwid = rs.getLong(3);
            qdateop = rs.getTimestamp(4);
            qlocal_xid= rs.getLong(5);
            LOGGERT.trace(  mythread+":" + "<Restart><From queue> <XID> " + txidstart + " <LSN> "+ lsn + "<WID> " + qwid+ "  <DATEOP> " +qdateop);
        } else {
            lsn = slotlsn;
            LOGGERT.trace(  mythread+":" + "<Restart><From slot> <LSN> " + lsn);
        }

        rs.close();

        PGReplicationStream stream =
                pgConnection
                        .getReplicationAPI()
                        .replicationStream()
                        .logical()
                        .withSlotName(RdbSlotBdr)
                        .withStartPosition(lsn)
                        //    .withSlotOption("proto_version",1)
                        //    .withSlotOption("publication_names", "pub1")
                        //  .withSlotOption("include-xids", true)
                        // .withSlotOption("include_transaction", true)
                        //    .withSlotOption("skip-empty-xacts", true)
                        .withStatusInterval(1, TimeUnit.SECONDS)
                        .start();
        ByteBuffer buffer;
        scanned = 0;
        boolean TxisOpen = false;
        boolean bufferIsNull = false;
        first = true;

        /* check  lsn Start Position is gt lsn riletto dalla coda  */
        beginLsn = qlocal_xid;
        qlsn = lsn.asString();
        while (true) {
            buffer =  stream.readPending();
	        mylsn = stream.getLastReceiveLSN().asString();
            LOGGERT.trace(  mythread+":" + "<Check> LSN from stream <> restart : " + mylsn + " ? " + qlsn);
            if (mylsn.equals(qlsn)) {
                LOGGERT.trace(  mythread+":" + "<Check><EQ> Stay in the loop: " + mylsn + " - " + qlsn);
                TimeUnit.MILLISECONDS.sleep(100L);
                continue;
            } else {
                LOGGERT.trace(  mythread+":" + "<Check><BREAK> Ok go on: " + mylsn + " - " + qlsn);

                if ( buffer == null) {
                    LOGGERT.trace(  mythread+":" + "<Check><buffer> is null ");
                    TimeUnit.MILLISECONDS.sleep(1000L);
                    continue ;
                }
                String s =  toString(buffer);
                mydmlpos = s.indexOf("#");
                tobeassignedxid = Long.parseLong(s.substring(0, mydmlpos));

                if ( qdateop == null || isXidDateopGreater(connectionRdb,tobeassignedxid, qdateop) ) {
                    LOGGERT.trace(  mythread+":" + "<Check><isXidDateopGreater>True");
                    break;
                }
            }
        }

      /* END LSN iniziale di restart
         BEGIN strutture threads WP / WC
       */
        TStructures tstr = new TStructures(stream, nodemst,threadn, filter,workpthread,workcthread,txidstart ,BATCH_SIZE, queue, map_txidlsn, map_lsndata, setTxid,map_minlsn_xid,connectionRdb, connection, new ConnInfo(host,user,pwd,port,db),new ConnInfo(rhost,ruser,rpwd,rport,rdb));
        IntStream.range(0, workpthread).mapToObj(i ->  new ConcurrentLinkedQueue<WalTrans>()).forEach(queueWP::add);
        IntStream.range(0, workcthread).mapToObj(i ->  new ConcurrentLinkedQueue<Long>()).forEach(queueWC::add);
        IntStream.range(0, workpthread).mapToObj(i -> new TWorkP(i,tstr,queueWP.get(i))).forEach(threads::add);
        IntStream.range(0, 1).mapToObj(i -> new TWorkD(i,tstr,queueWC)).forEach(disphreads::add);
        IntStream.range(0, workcthread).mapToObj(j -> new TWorkC(j,tstr,queueWC.get(j))).forEach(consthreads::add);

        /* Start TWorkers */

        for (TWorkP t : threads) {
            new Thread(t).start();
        }
        for (TWorkD t : disphreads) {
            new Thread(t).start();
        }
        for (TWorkC t : consthreads) {
            new Thread(t).start();
        }

        /* while running cycle */

        while (running.get()) {
            if (buffer == null) {

                if (first) {
                    for (int i=0;i< workpthread;i++) {
                        stp.execute("INSERT INTO dba.__events_ddl (ddl_id, wal_lsn, wal_txid, ddl_user, ddl_object,ddl_type,ddl_command,creation_timestamp)VALUES(-1,"+ (((BaseConnection) connection).haveMinimumServerVersion(ServerVersion.v10) ? "pg_current_wal_lsn()" : "pg_current_xlog_location()") +",txid_current(),CURRENT_USER,'NO_ACTIVITY_LSN_ACK','DML','UPSERT', NOW()) ON CONFLICT (ddl_id,ddl_origin) DO UPDATE SET  wal_lsn="+ currentlsnfunc +", wal_txid = txid_current(), creation_timestamp=NOW()");
                    }
                }
                first = false;
                LOGGERT.trace(  mythread+":" + "<Stream>null<buffer> ");
                if (!TxisOpen || bufferIsNull ) {
                    LOGGERT.trace(  mythread+":" + "<Buffer><is null:"+bufferIsNull +"><TxisOpen:"+TxisOpen+">sleep 3s ");
                    for (int i=0;i< workpthread;i++) {
                        stp.execute("INSERT INTO dba.__events_ddl (ddl_id, wal_lsn, wal_txid, ddl_user, ddl_object,ddl_type,ddl_command,creation_timestamp)VALUES(-1," + (((BaseConnection) connection).haveMinimumServerVersion(ServerVersion.v10) ? "pg_current_wal_lsn()" : "pg_current_xlog_location()") + ",txid_current(),CURRENT_USER,'NO_ACTIVITY_LSN_ACK','DML','UPSERT', NOW()) ON CONFLICT (ddl_id,ddl_origin) DO UPDATE SET  wal_lsn=" + currentlsnfunc + ", wal_txid = txid_current(), creation_timestamp=NOW()");
                    }
                    TimeUnit.MILLISECONDS.sleep(3000L);
                } else {
                    LOGGERT.trace(mythread + ":" + "<Buffer><is null:" + bufferIsNull + "><TxisOpen:" + TxisOpen + ">sleep 10ms ");
                    TimeUnit.MILLISECONDS.sleep(100L);
                }
                bufferIsNull = true;
            } else {
                bufferIsNull = false;
                TxisOpen = true;
                String s =  toString(buffer);
                mydmlpos = s.indexOf("#");
                tobeassignedxid = Long.parseLong(s.substring(0, mydmlpos));
                scanned = scanned + 1;

                if (tobeassignedxid == txidstart) {
                    LOGGERT.trace(mythread+ ":" + "<Transaction>"+tobeassignedxid+" is txidstart<continue>");
                    buffer  = stream.readPending();
                    continue;
                }

                if (( tobeassignedxid == lastxid ) && ( xidn< xidmax) ) {
                    xidn ++;
                } else {

                    if (tobeassignedxid != lastxid) {
                        TxisOpen = false;
                        beginLsn= beginLsn +1;
                        map_minlsn_xid.put(beginLsn,tobeassignedxid);
                        if (map_minlsn_xid.size() > maplimit ) {
                            LOGGERT.warn(mythread + ":"+ "<Alert><size> map_minlsn_xid exceed threshold of "+maplimit+" - sleep 1 sec!");
                            TimeUnit.MILLISECONDS.sleep(1000L);
                        }
                        LOGGERT.trace(mythread+ ":" + "<Transaction>"+tobeassignedxid+ "<Map><add><"+ beginLsn + "," + tobeassignedxid + "><map_minlsn_xid><Size>"+ map_minlsn_xid.size()  );
                        LOGGERT.trace(mythread+ ":" + "<Transaction><Previous>"+ lastxid + "#"+scanned);
                        scanned=0;
                    }
                    wpx++;
                    if (wpx%wpmax == 0) wpx = 0;
                    xidn = 1;
                    lastxid = tobeassignedxid;
                }
            LOGGERT.trace(mythread+ ":"+ "<Queue><add><TWorkP_"+wpx+"<Queue><Size>" + queueWP.get(wpx).size() );;
            queueWP.get(wpx).add(new WalTrans(stream.getLastReceiveLSN(), buffer, beginLsn ));
            LOGGERT.trace(mythread+ ":" + "<Transaction>"+tobeassignedxid+ "<Queue><add><TWorkP_"+wpx+"<"+xidn + "/"+xidmax+">");
            }
                buffer  = stream.readPending();

        }

        /* Stop TWorkers */
        for (TWorkP t : threads) {
            t.stopRunning();
        }

        for (TWorkD t : disphreads) {
            t.stopRunning();
        }

        for (TWorkC t : consthreads) {
            t.stopRunning();
        }

		} catch (SQLException e) {
			   e.printStackTrace();
                try {
                    inCaseOfException();
                } catch(SQLException sqlee) {
                    sqlee.printStackTrace();
                }

		}
		finally {
                try {
			if(stp != null)
                                stp.close();

                        if(st != null)
                                st.close();
                        } catch(SQLException sqlee) {
                                sqlee.printStackTrace();
                        } finally {  // Just to make sure that both con and stat are "garbage collected"
                                st = null;
                                stp = null;
                        }
                }

    }


    //select restart_lsn from pg_replication_slots where slot_name ='rdb_cina_bdr';
    private LogSequenceNumber getSlotLSN(String slotName) throws SQLException {
        try (Statement st = connection.createStatement()) {
            try (ResultSet rs = st.executeQuery("select restart_lsn from pg_replication_slots where slot_name ='" + slotName + "'")) {
                if (rs.next()) {
                    String lsn = rs.getString(1);
                    return LogSequenceNumber.valueOf(lsn);
                } else {
                    return LogSequenceNumber.INVALID_LSN;
                }
            }
        }
    }



    private String setCurrentLSNFunction() throws SQLException {
        try (Statement st = connection.createStatement()) {
                    if (((BaseConnection) connection).haveMinimumServerVersion(ServerVersion.v10)) {
                        return "pg_current_wal_lsn()";
                    } else {
                        return "pg_current_xlog_location()";
                    }
        }
    }


    private LogSequenceNumber getCurrentLSN() throws SQLException {
        try (Statement st = connection.createStatement()) {
            try (ResultSet rs = st.executeQuery("select "
                    + (((BaseConnection) connection).haveMinimumServerVersion(ServerVersion.v10)
                    ? "pg_current_wal_lsn()" : "pg_current_xlog_location()"))) {

                if (rs.next()) {
                    String lsn = rs.getString(1);
                    return LogSequenceNumber.valueOf(lsn);
                } else {
                    return LogSequenceNumber.INVALID_LSN;
                }
            }
        }
    }

    private void openReplicationConnection() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("user", user);
        properties.setProperty("password", pwd);
        properties.setProperty("reWriteBatchedInserts", "true");
        PGProperty.ASSUME_MIN_SERVER_VERSION.set(properties, "9.4");
        PGProperty.REPLICATION.set(properties, "database");
        PGProperty.PREFER_QUERY_MODE.set(properties, "simple");
        replicationConnection = DriverManager.getConnection(createUrl(), properties);
    }


   public static boolean isNullOrEmpty(String myString)
    {
         return myString == null || "".equals(myString);
    }

    public void loadProps(String[] args)   throws Exception  {
	String rdbbdr = System.getenv("RDBBDR");

	Options options = new Options();

        Option nodemstr = new Option("n", "nodemstr", true, "Please set desidered node master");
        nodemstr.setRequired(true);
        options.addOption(nodemstr);

	Option threadnum =  new Option("tn", "threadnum", true, "Please set thread number");
        threadnum.setRequired(false);
        options.addOption(threadnum);


	CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd = null;

	try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
	    LOGGERT.error(e.getMessage());
            formatter.printHelp("TCapt", options);
	     isInitialized = false ;
        }

        nodemst= cmd.getOptionValue("nodemstr");
	threadn = Integer.parseInt(cmd.getOptionValue("threadnum"));


	LOGGERT.info("Running TCapt for node :" + nodemst + " threadnum :" +threadn);


        if (isNullOrEmpty(rdbbdr))
        {
                  LOGGERT.error("RDBBDR variable should be set ");
		  isInitialized = false ;
        }

	if (isNullOrEmpty(nodemst))
	{
                  LOGGERT.error("NODEMST variable should be set ");
 		isInitialized = false ;
	}

        String rdbbdr_conf = rdbbdr + "/conf/" + nodemst + "_rdb_bdr.conf";

	if(!Files.isRegularFile(Paths.get(rdbbdr_conf))) {
		  LOGGERT.error(rdbbdr_conf + " not exists! ");
 		isInitialized = false ;
	}


        try (InputStream input = new FileInputStream(rdbbdr_conf)) {

            Properties prop = new Properties();

            prop.load(input);


            LOGGERT.trace("Configuration file: " +  rdbbdr_conf + " review");
            LOGGERT.trace("Primary Database: ");
            LOGGERT.trace("db " + prop.getProperty("db"));
            LOGGERT.trace("user " + prop.getProperty("user"));
            LOGGERT.trace("pwd " + prop.getProperty("pwd"));
            LOGGERT.trace("node " + prop.getProperty("node"));
            LOGGERT.trace("host " + prop.getProperty("host"));
	    LOGGERT.trace("");
            LOGGERT.trace("RDB database: ");
            LOGGERT.trace("rdb " + prop.getProperty("rdb"));
            LOGGERT.trace("ruser " + prop.getProperty("ruser"));
            LOGGERT.trace("rpwd " + prop.getProperty("rpwd"));
            LOGGERT.trace("rnode " + prop.getProperty("rnode"));
            LOGGERT.trace("rhost " + prop.getProperty("rhost"));
            LOGGERT.trace("walqtrunc " + prop.getProperty("walqtrunc"));
            LOGGERT.trace("batch_size " + prop.getProperty("batch_size"));
            LOGGERT.trace("filter " + prop.getProperty("filter"));
            LOGGERT.trace("workpthread " + prop.getProperty("workpthread"));
            LOGGERT.trace("workcthread " + prop.getProperty("workcthread"));
            LOGGERT.trace("xidmax " + prop.getProperty("xidmax"));
            LOGGERT.trace("maplimit " + prop.getProperty("maplimit"));
	    LOGGERT.trace("");

            host = prop.getProperty("host");
            user = prop.getProperty("user");
            port = Integer.valueOf(prop.getProperty("port"));
            pwd = prop.getProperty("pwd");
            db = prop.getProperty("db");
            node = prop.getProperty("node");
            walq = "_rdb_bdr.walq__" + node;
            LOGGERT.trace("walq " + walq);

            rhost = prop.getProperty("rhost");
            ruser = prop.getProperty("ruser");
            rport = Integer.valueOf(prop.getProperty("rport"));
            rpwd = prop.getProperty("rpwd");
            rdb = prop.getProperty("rdb");
		
            walqtrunc = Integer.valueOf(prop.getProperty("walqtrunc"));
            batch_size = Integer.valueOf(prop.getProperty("batch_size"));
            filter = Boolean.valueOf(prop.getProperty("filter"));

            workpthread = Integer.valueOf(prop.getProperty("workpthread"));
            workcthread = Integer.valueOf(prop.getProperty("workcthread"));
            xidmax = Integer.valueOf(prop.getProperty("xidmax"));
            maplimit = Integer.valueOf(prop.getProperty("maplimit"));

        } catch (IOException ex) {
	 	isInitialized = false ;
            ex.printStackTrace();
        }

    }



    public static void main(String[] args) {
        String pluginName = "rdblogdec";

        TCapt app = new TCapt(args);
	try {
        	app.loadProps(args);
      	}  catch (Exception ex) {
                LOGGERT.error( ex.getMessage());
        }

	 Thread t1 = new Thread(app);
	 t1.start();

    }
}



