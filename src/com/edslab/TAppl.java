/**
 * # -- Copyright (c) 2018-2022  Silvio Brandani <support@tcapture.net>. All rights reserved.
 *
 */

package com.edslab;

import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

/**
 * Logical Decoding TAppl
*/

public class TAppl implements Runnable {
    private static final Logger LOGGERA= LogManager.getLogger(TAppl.class.getName());
    private AtomicBoolean running = new AtomicBoolean(false);
    private String mythread = "";
    private String host = "";
    private String user = "";
    private String pwd = "";
    private int port = 5432;
    private String db = "";
    private String node = "";
    private String nodemst = "";
    private String nodeslv = "";
    private int threadn = 0;
    private String walq = "";
    private String rhost = "";
    private String ruser = "";
    private String rpwd = "";
    int rport = 5432;
    private String rdb = "";
    int walqtrunc = 9;
    int batch_size = 1000;
    private String loglevel = "";
    private boolean filter = false;
    boolean log_hist = false;
    boolean isMaster = false;
    private Connection connection;
    private Connection connectionRdb;
    public boolean isInitialized = true;
    boolean isException=false;
    int workpthread = 2;
    int workcthread =2;
    int xidmax = 10;
    int maplimit =10000;

    private static String toString(ByteBuffer buffer) {
        int offset = buffer.arrayOffset();
        byte[] source = buffer.array();
        int length = source.length - offset;
        return new String(source, offset, length);
    }

    private String createUrl() {
        return "jdbc:postgresql://" + host + ':' + port + '/' + db;
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

    private String createRdbUrl() {
        // da redirezionare sul rdb rhost rport ruser rpwd quando i walq__nodemst saranno nel rdb__nodeslv
        return "jdbc:postgresql://" + rhost + ':' + rport + '/' + rdb;
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

   public TAppl(String[] args) {
	try {
	   loadProps(args);
 	}  catch (Exception ex) {
	        LOGGERA.error( ex.getMessage());
       	}
   }


    public void shutDown()
    {
        running.set(false);
        LOGGERA.info(  mythread+":  Shutting Down");
    }



     public void stopRunning()
    {
        running.set(false);
        LOGGERA.info(  mythread+":  Stop Running");
    }

    public void interrupt() {
        running.set(false);
        Thread.currentThread().interrupt();
        LOGGERA.info(  mythread+":  Interrupting");

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


    public void inCaseOfException() throws SQLException {
        String qerror;
        Connection connectionRdbE = null;
      //  qerror = "update _rdb_bdr.tc_process set n_state ='down' , n_dateop= now() where n_pid= " +threadn + " and n_mstr = '" +nodemst+ "' and  n_type = 'M';" ;
        qerror = "update _rdb_bdr.tc_process set n_operation ='shutdown' , n_dateop= now() where n_pid= " +threadn + " and n_mstr = '" +nodemst+ "' and  n_type = 'S';" ;

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
        } finally {
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


      private boolean checkIsMaster() throws  Exception {
        String qq= " SELECT EXISTS(select 1 from _rdb_bdr.tc_process where n_type='M' ) ; ";
        try (PreparedStatement preparedStatement = connectionRdb.prepareStatement(qq)) {
            try (ResultSet rs = preparedStatement.executeQuery()) {
                if (null != rs && rs.next())

                    return  rs.getBoolean(1);
            }
        }

        return false;
    }

   public void run()  {  

   	mythread = "TA_"+ nodemst +"_"+ threadn; 
	Thread.currentThread().setName("TA-"+ nodemst );
	LOGGERA.info(mythread +" is in running state");
		
        running.set(true);

        try {

        createConnection();
        createConnectionRdb();
        isMaster = checkIsMaster();
        runwal();

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
                        } catch(SQLException sqlee) {
                                sqlee.printStackTrace();
                        } finally {  // Just to make sure that both con and stat are "garbage collected"
                                connection = null;
                                connectionRdb = null;
                        }
                }
  }



   public void runwal() throws Exception {

       int ret_rows = 0;
       int limit = batch_size;
       long loc_xid_offset = 0;

        /*
             manageXid variables
        */
       int scanned = 0;
       long mywid = 0;
       long myxid = 0;
       String mylsn = "";
       String mydata = "";
       Date mydateop = null;
       long mylocal_xid = 0;
       boolean managed;
       boolean isException = false;
       long txidstart = 0;
       long lastWid = 0;
        /*
               rotazione TWAP variables
        */
       long lastxid = 0;
       long tobeassignedxid = 0;
       long beginLsn = 1;
       int wpmax = workpthread;
       int wpx = 0;
       int xidn = 0;

	/*
		TWorks Structures
	*/

       List<TWorkAP> aprodthreads = new ArrayList<>();
       List<TWorkAC> aconsthreads = new ArrayList<>();
       List<TWorkAD> adisphreads = new ArrayList<>();
       Queue<QueueTrans> queue = new ConcurrentLinkedQueue<QueueTrans>();
       Map<Long, Set<String>> map_txidlsn = new ConcurrentHashMap<Long, Set<String>>();
       Map<String, String> map_lsndata = new ConcurrentHashMap<String, String>();
       Map<String, Long> map_lsnwid = new ConcurrentHashMap<String, Long>();
       Map<Long, Set<Integer>> setTxid = new ConcurrentSkipListMap<Long, Set<Integer>>();
       Map<Long, Long> map_minlsn_xid = new ConcurrentSkipListMap<Long, Long>(); // or HashMap?
       int BATCH_SIZE = 500;
       List<Queue<QueueTrans>> queueWAP = new ArrayList<>();
       List<Queue<Long>> queueWAC = new ArrayList<>();
       boolean bufferIsNull = false;
       int sleepn;
       int sleepb;
       boolean first = true;

       String queryScanXidFirstOld =
               "select distinct xid,dateop from walq__" + nodemst + " where dateop >  ( select  dateop from (" +
                       " select dateop from _rdb_bdr.walq__" + nodemst + "_offset where xid_offset = 0  " +
                       " union  " +
                       " (select b.dateop from _rdb_bdr.walq__" + nodemst + "_offset a, _rdb_bdr.walq__" + nodemst + " b where a.xid_offset= b.xid limit 1)" +
                       " union" +
                       " (select dateop from _rdb_bdr.walq__" + nodemst + " where xid = " + loc_xid_offset + " and " + loc_xid_offset + " > 0 limit 1) " +
                       ")  " +
                       "foo order by dateop desc limit 1 ) " +
                       "order by dateop limit 10; ";

       String queryScanXidFirst =
               "select distinct xid,local_xid from walq__" + nodemst + " where local_xid >  ( select  local_xid from (" +
                       " select local_xid_offset as \"local_xid\" from _rdb_bdr.walq__" + nodemst + "_offset where xid_offset = 0  " +
                       " union  " +
                       " (select b.local_xid from _rdb_bdr.walq__" + nodemst + "_offset a, _rdb_bdr.walq__" + nodemst + " b where a.xid_offset= b.xid limit 1)" +
                       // " union " +
                       // " (select local_xid from _rdb_bdr.walq__"+ nodemst+" where xid = " + loc_xid_offset +" and " +loc_xid_offset+" > 0 limit 1) " +
                       ")  " +
                       "foo order by local_xid desc limit 1 ) " +
                       "order by local_xid limit 10; ";


       String queryScanXid = "";
       TAStructures tstr = new TAStructures(node, isMaster, nodemst, threadn, filter, workpthread, workcthread, txidstart, BATCH_SIZE, queue, map_txidlsn, map_lsndata, map_lsnwid, setTxid, map_minlsn_xid, connectionRdb, connection, new ConnInfo(host, user, pwd, port, db), new ConnInfo(rhost, ruser, rpwd, rport, rdb));
       IntStream.range(0, workpthread).mapToObj(i -> new ConcurrentLinkedQueue<QueueTrans>()).forEach(queueWAP::add);
       IntStream.range(0, workcthread).mapToObj(i -> new ConcurrentLinkedQueue<Long>()).forEach(queueWAC::add);
       IntStream.range(0, workpthread).mapToObj(i -> new TWorkAP(i, tstr, queueWAP.get(i))).forEach(aprodthreads::add);
       IntStream.range(0, 1).mapToObj(i -> new TWorkAD(i, tstr, queueWAC)).forEach(adisphreads::add);
       IntStream.range(0, workcthread).mapToObj(j -> new TWorkAC(j, tstr, queueWAC.get(j))).forEach(aconsthreads::add);


       try {
           /* Start TWorkers */

           for (TWorkAP t : aprodthreads) {
               new Thread(t).start();
           }
           for (TWorkAD t : adisphreads) {
               new Thread(t).start();
           }
           for (TWorkAC t : aconsthreads) {
               new Thread(t).start();
           }

           /* while running cycle */

           while (running.get()) {
               //try {
               String queryScanXidAfter = " select distinct xid,local_xid from walq__" + nodemst + " where local_xid > (select   local_xid from _rdb_bdr.walq__" + nodemst + " where xid = " + loc_xid_offset + " limit 1) order by local_xid limit " + limit;

               if (first && loc_xid_offset == 0) {
                   queryScanXid = queryScanXidFirst;
               } else {
                   first = false;
                   queryScanXid = queryScanXidAfter;
               }
               LOGGERA.trace(mythread + ":" + "<Query><Scan>" + loc_xid_offset + "");
               try (
                       PreparedStatement preparedStatement = connectionRdb.prepareStatement(queryScanXid)
               ) {
                   try (
                           ResultSet rs = preparedStatement.executeQuery()
                   ) {
                       while (rs.next()) {
                           loc_xid_offset = rs.getLong(1);
                           LOGGERA.trace(mythread + ":" + "<Transaction>" + loc_xid_offset + "<Get>");

                           PreparedStatement preparedStatement2 =
                                  // connectionRdb.prepareStatement("Select wid, lsn, xid,data, dateop ,local_xid from walq__" + nodemst + " where  xid=?  order by wid");
                                   connectionRdb.prepareStatement("Select wid, lsn, xid,data, dateop ,local_xid from walq__" + nodemst + " where  xid=?  order by lsn");
                           preparedStatement2.setLong(1, loc_xid_offset);
                           ResultSet rs2 = preparedStatement2.executeQuery();
                           managed = false;
                           connection.setAutoCommit(true);
                           connectionRdb.setAutoCommit(true);
                           // scanned =0;

                           while (rs2.next()) {
                               mywid = rs2.getLong(1);
                               mylsn = rs2.getString(2);
                               myxid = rs2.getLong(3);
                               mydata = rs2.getString(4);
                               mydateop = rs2.getTimestamp(5);
                               mylocal_xid = rs2.getLong(6);
                               scanned = scanned + 1;
                               LOGGERA.trace(mythread + ":" + "<Transaction>" + myxid + "<Detail>" + "<dateop:" + mydateop + " wid:" + mywid + " local_xid:" + mylocal_xid + " sql:" + mydata + " >");

                        /*
                           Start management
                         */
                               tobeassignedxid = myxid;
                               lastWid = mywid;

                               if ((tobeassignedxid == lastxid) && (xidn < xidmax)) {
                                   xidn++;
                               } else {
                                   if (tobeassignedxid != lastxid) {
                                       beginLsn = mylocal_xid;
                                       map_minlsn_xid.put(beginLsn, tobeassignedxid);
                                       if (map_minlsn_xid.size() > maplimit) {
                                           LOGGERA.trace(mythread + ":" + "<Alert><size> map_minlsn_xid exceed threshold of " + maplimit + " - sleep 1 sec!");
                                           TimeUnit.MILLISECONDS.sleep(1000L);
                                       }
                                       LOGGERA.trace(mythread + ":" + "<Transaction>" + tobeassignedxid + "<Map><add><" + beginLsn + "," + tobeassignedxid + "><map_minlsn_xid><Size>" + map_minlsn_xid.size());
                                       LOGGERA.trace(mythread + ":" + "<Transaction><Previous>" + lastxid + "#" + scanned);
                                       scanned = 0;
                                   }
                                   wpx++;
                                   if (wpx % wpmax == 0) wpx = 0;
                                   xidn = 1;
                                   lastxid = tobeassignedxid;
                               }
                               LOGGERA.trace(mythread + ":" + "<Queue><add><TWorkAP_" + wpx + "<Queue><Size>" + queueWAP.get(wpx).size());
                               ;
                               queueWAP.get(wpx).add(new QueueTrans(mylsn, tobeassignedxid, beginLsn, mydata, mywid));
                               LOGGERA.trace(mythread + ":" + "<Transaction>" + tobeassignedxid + "<Queue><add><TWorkAP_" + wpx + "<" + xidn + "/" + xidmax + ">");
                        /*
                           End management
                         */
                           } // End While interno

                       } // End While esterno
                   }
               }

               if (tobeassignedxid == lastxid) {
                   TimeUnit.MILLISECONDS.sleep(3000L);
               }
               TimeUnit.MILLISECONDS.sleep(100L);
	  /*} catch (InterruptedException e){
                Thread.currentThread().interrupt();
                 LOGGERA.error("  Thread was interrupted, Failed to complete operation:"+ mythread);
          }
          */
           } // end while

           /* Stop TWorkers */
           for (TWorkAP t : aprodthreads) {
               //t.stop();
               t.stopRunning();
           }
           for (TWorkAD t : adisphreads) {
               t.stopRunning();
           }
           for (TWorkAC t : aconsthreads) {
               t.stopRunning();
               //t.stop();
           }

       } catch (SQLException e) {
        e.printStackTrace();
        try {
            inCaseOfException();
        } catch(SQLException sqlee) {
            sqlee.printStackTrace();
        }

    }



}


 public static boolean isNullOrEmpty(String myString)
    {
         return myString == null || "".equals(myString);
    }


    public synchronized void loadProps(String[] args)  throws Exception {

        String rdbbdr = System.getenv("RDBBDR");

	Options options = new Options();

   	Option rnodemstr = new Option("rn", "rnodemstr", true, "Please set desidered remote node master");
   	rnodemstr.setRequired(true);
   	options.addOption(rnodemstr);

	Option nodeslvs = new Option("s", "nodeslvs", true, "Please set desidered node slave");
        nodeslvs.setRequired(true);
        options.addOption(nodeslvs);

	Option threadnum =  new Option("tn", "threadnum", true, "Please set thread number");
        threadnum.setRequired(false);
	options.addOption(threadnum);

 	CommandLineParser parser = new DefaultParser();
   	HelpFormatter formatter = new HelpFormatter();
   	CommandLine cmd = null;

 	try {
       		cmd = parser.parse(options, args);
   	} catch (ParseException e) {
       		 LOGGERA.error(e.getMessage());
        	formatter.printHelp("TAppl", options);
		isInitialized = false ;
   	}

   	nodemst= cmd.getOptionValue("rnodemstr");
   	nodeslv= cmd.getOptionValue("nodeslvs");
   	threadn = Integer.parseInt(cmd.getOptionValue("threadnum"));

	LOGGERA.info("Running TAppl consumer " + nodeslv + " for node " + nodemst);

        if (isNullOrEmpty(rdbbdr))
        {
                 LOGGERA.error("RDBBDR variable should be set ");
		isInitialized = false ;
        }

        if (isNullOrEmpty(nodemst))
        {
                 LOGGERA.error("NODEMST variable should be set ");
		isInitialized = false ;
        }

        if (isNullOrEmpty(nodeslv))
        {
                LOGGERA.error("NODESLV variable should be set ");
		isInitialized = false ;
        }

        String nodemst_rdbbdr_conf = rdbbdr + "/conf/" + nodemst + "_rdb_bdr.conf";
        String nodeslv_rdbbdr_conf = rdbbdr + "/conf/" + nodeslv + "_bdr_rdb.conf";

        if(!Files.isRegularFile(Paths.get(nodemst_rdbbdr_conf))) {
                 LOGGERA.error(nodemst_rdbbdr_conf + " not exists! ");
		 isInitialized = false ;
		 throw new Exception(nodemst_rdbbdr_conf + " not exists!  Exit");
        }

        if(!Files.isRegularFile(Paths.get(nodeslv_rdbbdr_conf))) {
                 LOGGERA.error(nodeslv_rdbbdr_conf + " not exists! ");
		 isInitialized = false ;
		 throw new Exception(nodemst_rdbbdr_conf + " not exists!  Exit");

        }

        try (InputStream input = new FileInputStream(nodeslv_rdbbdr_conf)) {

            Properties prop = new Properties();

            prop.load(input);

            LOGGERA.trace("Configuration file: " +  nodeslv_rdbbdr_conf + " review");
		
            LOGGERA.trace("Master node: "+ nodemst);
            LOGGERA.trace("Slave Database: ");
            LOGGERA.trace("db " + prop.getProperty("db"));
            LOGGERA.trace("user " + prop.getProperty("user"));
            LOGGERA.trace("pwd " + prop.getProperty("pwd"));
            LOGGERA.trace("node " + prop.getProperty("node"));
            LOGGERA.trace("host " + prop.getProperty("host"));
            LOGGERA.trace("");
            LOGGERA.trace("RDB database: ");
            LOGGERA.trace("rdb " + prop.getProperty("rdb"));
            LOGGERA.trace("ruser " + prop.getProperty("ruser"));
            LOGGERA.trace("rpwd " + prop.getProperty("rpwd"));
            LOGGERA.trace("rnode " + prop.getProperty("rnode"));
            LOGGERA.trace("rhost " + prop.getProperty("rhost"));
            LOGGERA.trace("walqtrunc " + prop.getProperty("walqtrunc"));
            LOGGERA.trace("batch_size " + prop.getProperty("batch_size"));
            LOGGERA.trace("filter " + prop.getProperty("filter"));
            LOGGERA.trace("log_hist " + prop.getProperty("log_hist"));
            LOGGERA.trace("workpthread " + prop.getProperty("workpthread"));
            LOGGERA.trace("workcthread " + prop.getProperty("workcthread"));
            LOGGERA.trace("xidmax " + prop.getProperty("xidmax"));
            LOGGERA.trace("maplimit " + prop.getProperty("maplimit"));
            LOGGERA.trace("");

            host = prop.getProperty("host");
            user = prop.getProperty("user");
            port = Integer.valueOf(prop.getProperty("port"));
            pwd = prop.getProperty("pwd");
            db = prop.getProperty("db");
            node = prop.getProperty("node");
            walq = "_rdb_bdr.walq__" + node;

            rhost = prop.getProperty("rhost");
            ruser = prop.getProperty("ruser");
            rport = Integer.valueOf(prop.getProperty("rport"));
            rpwd = prop.getProperty("rpwd");
            rdb = prop.getProperty("rdb");

            walqtrunc = Integer.valueOf(prop.getProperty("walqtrunc"));
            batch_size = Integer.valueOf(prop.getProperty("batch_size"));
	    
            filter = Boolean.valueOf(prop.getProperty("filter"));
    	    log_hist = Boolean.valueOf(prop.getProperty("log_hist"));
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

        TAppl app = new TAppl(args);

	try {
        	app.loadProps(args);
         }  catch (Exception ex) {
                LOGGERA.error( ex.getMessage());
         }

	Thread t1 = new Thread(app);
        t1.start();  

    }
}

