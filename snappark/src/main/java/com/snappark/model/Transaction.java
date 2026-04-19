package com.snappark.model;
import java.time.LocalDateTime;

public class Transaction {
    private int           id;
    private int           sessionId;
    private double        amount;
    private double        discount;
    private LocalDateTime paymentTime;
    private String        receipt;
    private String        paymentMethod;
    // v2.0 fields
    private double        fineAmount;
    private String        exitPin;
    private String        sessionPin;

    public Transaction() {}

    public int           getId()            { return id; }
    public void          setId(int id)      { this.id = id; }
    public int           getSessionId()     { return sessionId; }
    public void          setSessionId(int s){ this.sessionId = s; }
    public double        getAmount()        { return amount; }
    public void          setAmount(double a){ this.amount = a; }
    public double        getDiscount()      { return discount; }
    public void          setDiscount(double d){ this.discount = d; }
    public LocalDateTime getPaymentTime()   { return paymentTime; }
    public void          setPaymentTime(LocalDateTime p){ this.paymentTime = p; }
    public String        getReceipt()       { return receipt; }
    public void          setReceipt(String r){ this.receipt = r; }
    public String        getPaymentMethod() { return paymentMethod != null ? paymentMethod : "CASH"; }
    public void          setPaymentMethod(String m){ this.paymentMethod = m; }

    // v2.0 getters/setters
    public double        getFineAmount()    { return fineAmount; }
    public void          setFineAmount(double f) { this.fineAmount = f; }
    public String        getExitPin()       { return exitPin; }
    public void          setExitPin(String p){ this.exitPin = p; }
    public String        getSessionPin()    { return sessionPin; }
    public void          setSessionPin(String p){ this.sessionPin = p; }

    public double getTotalAmount() {
        return Math.round((amount + fineAmount - (amount * discount / 100.0)) * 100.0) / 100.0;
    }
}