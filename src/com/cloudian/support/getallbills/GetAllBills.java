/**
 * 
 */
package com.cloudian.support.getallbills;

import java.io.File;
import java.io.IOException;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Consts;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.fluent.Request;
import org.apache.http.util.EntityUtils;

import com.google.gson.Gson;

/**
 * For all groups, get all users, and generate the bill, writing the output as CSV to a specified file.
 * 
 */
public class GetAllBills {

    class GroupObj {
        public String active;
        public String groupId;
        public String groupName;
        GroupObj() {}
    }
    
    class UserObj {
        public String userId;
        public String userType;
        public String groupId;
        public String canonicalUserId;
        UserObj() {}
    }

    class BillObj {
        public String total;
        public String currency;
        public String groupId;
        public String userId;
        public String regionBills;
        public String billID;
        public String notes;
        public String startCal;
        public String endCal;
        BillObj() {}
    }
    
    
    static ResponseHandler<String> myHandler = new ResponseHandler<String>() {
        @Override
        public String handleResponse(final HttpResponse res) throws IOException {
            String status = res.getStatusLine().toString();
            if (!StringUtils.contains(status, " 20")) {
                System.out.println("Error: " + status);
                System.exit(1);
            }
            if (StringUtils.contains(status, " 204 ")) {
                return "";
            }
            return EntityUtils.toString(res.getEntity(), Consts.UTF_8);
        }
    };
    
    /**
     * @param args
     */
    public static void main(String[] args) {
        try {
            // Process args
            Options options = new Options();
            options.addOption("h", true, "host. Default:localhost");
            options.addOption("p", true, "port. Default:18081");
            options.addOption("m", true, "bill month in format yyyyMM.  Default:201405");
            options.addOption("f", true, "output file. Default: /tmp/getallbills.csv");
            options.addOption("help", false, "print this message");
            CommandLine cmd = null;
            try {
                cmd = new BasicParser().parse(options, args);
            } catch(ParseException exp) {
                System.err.println( "Parsing failed.  Reason: " + exp.getMessage() );
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("getallbills", options);
                System.exit(1);
            } 
            if (cmd.hasOption("help")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("getallbills", options);
                System.exit(1);
            }
            final String hostport = "http://" + cmd.getOptionValue("h", "localhost") + ":" + cmd.getOptionValue("p", "18081");
            final String billMonth = cmd.getOptionValue("m", "201405");
            final String outputPath = cmd.getOptionValue("f", "/tmp/getallbills.csv");

            // Create output file and write header line.
            File file = new File(outputPath);
            FileUtils.writeStringToFile(file, "billId,currency,groupId,userId,regionBills,billId,notes,startCal,endCal");

            // Get groups
            String sURL = hostport + "/group/list";
            System.out.println("GET " + sURL);
            String body = Request.Get(sURL)
                    .connectTimeout(10000)
                    .socketTimeout(10000)
                    .execute().handleResponse(myHandler);
            System.out.println(body);
            Gson gson = new Gson();
            GroupObj[] groups = gson.fromJson(body, GroupObj[].class);
            
            // For each group, get user list
            for (GroupObj g : groups) {
                sURL = hostport + "/user/list?groupId=" + g.groupId + "&userType=all&userStatus=all";
                System.out.println("  GET " + sURL);
                body = Request.Get(sURL)
                    .connectTimeout(10000).socketTimeout(10000).execute().handleResponse(myHandler);
                System.out.println("  " + body);
                UserObj[] users = gson.fromJson(body, UserObj[].class);
                // For each user, get bill
                for (UserObj u : users) {
                    sURL = hostport + "/billing?canonicalUserId=" + u.canonicalUserId + "&billingPeriod=" + billMonth;
                    System.out.println("    POST " + sURL);
                    body = Request.Post(sURL)
                            .connectTimeout(60000)
                            .socketTimeout(60000)
                            .useExpectContinue()
                            .version(HttpVersion.HTTP_1_1)
                            .execute().handleResponse(myHandler);
                    System.out.println("    " + body);
                    BillObj[] bills = gson.fromJson(body, BillObj[].class);
                    if (bills == null) {
                        continue;
                    }
                    // Write as CSV.
                    String csv = null;
                    for (BillObj b : bills) {
                        csv = b.billID + "," + b.currency + "," + b.groupId
                                + "," + b.userId + "," + b.regionBills + "," + b.billID
                                + "," + b.notes + "," + b.startCal + "," + b.endCal;
                        FileUtils.writeStringToFile(file, csv, Consts.UTF_8);
                    }
                   
                }
            }
            System.out.println("***DONE***");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
