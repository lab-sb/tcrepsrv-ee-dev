/**
 * # -- Copyright (c) 2018-2022  Silvio Brandani <support@tcapture.net>. All rights reserved.
 *
 */

package com.edslab;

class ConnInfo {
    ConnInfo(String a , String b, String c , int d, String e){
        host = a;
        user = b;
        pwd = c;
        port =d;
        db = e;

    }

    final String host;
    final String user;
    final String pwd;
    final int port;
    final String db;
}