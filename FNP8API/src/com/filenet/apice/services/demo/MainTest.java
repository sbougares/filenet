package com.filenet.apice.services.demo;

import javax.security.auth.Subject;

import com.filenet.api.core.Connection;
import com.filenet.api.core.Domain;
import com.filenet.api.core.Factory;
import com.filenet.api.util.UserContext;

public class MainTest {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		String uri = "http://localhost:9080/wsi/FNCEWS40MTOM";
        String user = "gcd_admin";
        String pass = "Solution123";
        UserContext uc = UserContext.get();
		Connection con = Factory.Connection.getConnection(uri);
        Subject sub = UserContext.createSubject(con,user,pass,null);
        uc.pushSubject(sub);
        Domain dom = Factory.Domain.fetchInstance(con, null, null);
        String domainName = dom.get_Name();
		
        
        	

	}

}
