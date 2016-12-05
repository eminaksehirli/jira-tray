package com.dataspark.jira;

public class Creds {

  public final String user;
  public final String pass;

  public Creds(String user, String pass) {
    this.user = user;
    this.pass = pass;
  }

  public String getUser() {
    return user;
  }

  public String getPass() {
    return pass;
  }

}
