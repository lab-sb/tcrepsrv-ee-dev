/**
 * # -- Copyright (c) 2018-2022  Silvio Brandani <support@tcapture.net>. All rights reserved.
 *
 */

package com.edslab;

import org.postgresql.replication.LogSequenceNumber;

import java.nio.ByteBuffer;

class QueueTrans {
    QueueTrans(String a , Long b, Long c ,String d, Long w){
        lsn = a;
        bXid = b;
        beginLsn = c;
        data =d;
        wid =w;
    }

    final String lsn;
    final Long bXid;
    final Long beginLsn;
    final String data;
    final Long wid;
}