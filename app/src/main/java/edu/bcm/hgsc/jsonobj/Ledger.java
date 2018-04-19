package edu.bcm.hgsc.jsonobj;

/**
 * Created by sl9 on 2/22/18.
 */


public class Ledger {


    /**
     * hash : 27558dc3dab1eec58a3c40a4e6c2c0db3992a53f
     * localid : smp01
     * filepath : /Users/jordanj/development/python_ledger/report1.xml
     * patientid : pat01
     * rptid : 1
     * date : 2018-01-26T12:59:45
     */

    private String hash;
    private String localid;
    private String filepath;
    private String patientid;
    private String rptid;
    private String date;

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getLocalid() {
        return localid;
    }

    public void setLocalid(String localid) {
        this.localid = localid;
    }

    public String getFilepath() {
        return filepath;
    }

    public void setFilepath(String filepath) {
        this.filepath = filepath;
    }

    public String getPatientid() {
        return patientid;
    }

    public void setPatientid(String patientid) {
        this.patientid = patientid;
    }

    public String getRptid() {
        return rptid;
    }

    public void setRptid(String rptid) {
        this.rptid = rptid;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }
}
