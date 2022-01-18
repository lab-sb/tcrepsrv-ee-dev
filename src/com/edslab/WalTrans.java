/**
 * # -- Copyright (c) 2018-2022  Silvio Brandani <support@tcapture.net>. All rights reserved.
 *
 */
package com.edslab;
import org.postgresql.replication.LogSequenceNumber;
import java.nio.ByteBuffer;

class WalTrans {
    WalTrans( LogSequenceNumber a , ByteBuffer b, Long c ){
        lsn = a;
        bb = b;
        beginLsn = c;
    }

    final LogSequenceNumber lsn;
    final ByteBuffer bb;
    final Long beginLsn;
}