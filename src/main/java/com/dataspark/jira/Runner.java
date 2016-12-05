package com.dataspark.jira;

import static java.awt.SystemTray.getSystemTray;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static javax.swing.BoxLayout.PAGE_AXIS;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.prefs.Preferences;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import net.rcarz.jiraclient.BasicCredentials;
import net.rcarz.jiraclient.Issue;
import net.rcarz.jiraclient.Issue.SearchResult;
import net.rcarz.jiraclient.JiraClient;
import net.rcarz.jiraclient.JiraException;
import net.rcarz.jiraclient.RestException;

public class Runner {
  private static final String baseUrl = "https://dataspark.atlassian.net/";
  private static final String Pref_Key = "com.dataspark.jira";
  private static final String User_Key = "jiraUser";
  private static String browseUrl = baseUrl + "browse/";
  private static JiraClient jira;

  private static int width = 300;
  private static int height = 50;
  private static TrayIcon trayIcon;
  private static ScheduledExecutorService scheduler;
  private static long delay = 300;
  private static JFrame loginFrame;
  private static JTextField userTF;
  private static JPasswordField passF;
  private static Creds creds;

  public static void main(String[] args) throws AWTException {
    Image image = newImageWithText(width, height, "|= JIRA =|");

    PopupMenu pm = new PopupMenu("JIRA connector");
    MenuItem mi = new MenuItem("Login");
    mi.addActionListener(e -> showLoginFrame());
    pm.add(mi);
    addQuitTo(pm);

    trayIcon = new TrayIcon(image);
    trayIcon.setPopupMenu(pm);
    getSystemTray().add(trayIcon);

    scheduler = Executors.newScheduledThreadPool(1);
  }

  private static void showLoginFrame() {
    Preferences pref = Preferences.userRoot().node(Pref_Key);
    String knownUser = pref.get(User_Key, "");
    loginFrame = new JFrame();
    loginFrame.getContentPane().setLayout(new BoxLayout(loginFrame.getContentPane(), PAGE_AXIS));

    userTF = new JTextField(knownUser, 40);
    passF = new JPasswordField(40);

    userTF.addActionListener(l -> login());
    passF.addActionListener(l -> login());

    loginFrame.add(userTF);
    loginFrame.add(passF);

    loginFrame.setSize(300, 100);
    loginFrame.setVisible(true);
  }


  private static void login() {
    loginFrame.dispose();
    creds = new Creds(userTF.getText(), String.valueOf(passF.getPassword()));
    if (jira == null) {
      BasicCredentials basCreds = new BasicCredentials(creds.getUser(), creds.getPass());
      jira = new JiraClient(baseUrl, basCreds);
    }
    try {
      refreshJira();
      Preferences pref = Preferences.userRoot().node(Pref_Key);
      pref.put(User_Key, creds.user);
    } catch (LoginException e) {
      JOptionPane.showMessageDialog(loginFrame,
          "Username/password did not work. Details are on the log. Try again.");
      jira = null;
      showLoginFrame();
    }
  }

  private static void refreshJira() {
    try {
      TrayIcon newTrayIcon = createIconWithJira();
      getSystemTray().remove(trayIcon);
      trayIcon = newTrayIcon;
      getSystemTray().add(trayIcon);
    } catch (AWTException e) {
      e.printStackTrace();
    } catch (JiraException je) {
      if (je.getCause() instanceof RestException) {
        if (((RestException) je.getCause()).getHttpStatusCode() == 401) {
          je.printStackTrace();
          throw new LoginException();
        }
      }
      je.printStackTrace();
    }

    scheduler.schedule(() -> refreshJira(), delay, SECONDS);
  }

  private static TrayIcon createIconWithJira() throws JiraException {
    SearchResult res =
        jira.searchIssues(
            "status in (Review, Develop, \"Code Review\", \"PM Review\") AND assignee in (currentUser())");

    System.out.println("LOG: Refreshed. Got " + res.total + " issues from JIRA.");
    List<Issue> underDev =
        res.issues.stream().filter(i -> i.getStatus().getName().equals("Develop"))
            .collect(toList());
    List<Issue> underReview =
        res.issues.stream().filter(i -> i.getStatus().getName().equals("Code Review"))
            .collect(toList());

    String text = devTextFor(underDev) + " " + revTextFor(underReview);

    Image image = newImageWithText(width, height, text);

    PopupMenu pm = new PopupMenu("JIRA connector");
    if (underDev.isEmpty()) {
      pm.add(newDisabledMenuItem("You are not developing anything!〈( ^.^)ノ"));
    }
    underDev.forEach(i -> createAndAddTo(pm, i));

    TrayIcon trayIcon = new TrayIcon(image);

    pm.addSeparator();
    pm.add(newDisabledMenuItem("Under Review"));
    if (underReview.isEmpty()) {
      pm.add(newDisabledMenuItem("All reviewed! ✓"));
    }
    underReview.forEach(i -> createAndAddTo(pm, i));

    addRefreshTo(pm);
    addQuitTo(pm);

    trayIcon.setPopupMenu(pm);
    return trayIcon;
  }

  private static String devTextFor(List<Issue> underDev) {
    String devText;
    if (underDev.isEmpty()) {
      devText = "〈( ^.^)ノ";
    } else {
      devText = underDev.stream().map(i -> i.getKey()).collect(joining(", "));
    }
    return devText;
  }

  private static String revTextFor(List<Issue> underReview) {
    String revText;
    if (underReview.isEmpty()) {
      revText = "(✓)";
    } else {
      revText = "(" + underReview.size() + ")";
    }
    return revText;
  }


  private static void addRefreshTo(PopupMenu pm) {
    pm.addSeparator();
    MenuItem mi = new MenuItem("Refresh");
    mi.addActionListener(l -> refreshJira());
    pm.add(mi);
  }

  private static void addQuitTo(PopupMenu pm) {
    pm.addSeparator();
    MenuItem mi = new MenuItem("Quit");
    mi.addActionListener(l -> System.exit(0));
    pm.add(mi);
  }

  private static void createAndAddTo(PopupMenu pm, Issue i) {
    MenuItem mi = new MenuItem(i.getKey() + ": " + i.getSummary());
    mi.addActionListener(e -> openLink(browseUrl + i.getKey()));
    pm.add(mi);
  }

  private static MenuItem newDisabledMenuItem(String label) {
    MenuItem mi = new MenuItem(label);
    mi.setEnabled(false);
    return mi;
  }

  private static void openLink(String uri) {
    try {
      openLink(new URI(uri));
    } catch (URISyntaxException e) {
      e.printStackTrace();
    }
  }

  private static void openLink(URI uri) {
    Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
    if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
      try {
        desktop.browse(uri);
      } catch (Exception e) {
        System.err.println(e);
      }
    }
  }

  private static Image newImageWithText(int width, int height, String str) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = (Graphics2D) image.getGraphics();
    g.setColor(Color.BLACK);
    Font font = new Font("Arial", Font.PLAIN, (int) (height * .6));
    g.setFont(font);
    FontMetrics fontMetrics = g.getFontMetrics(font);
    int strHeight = fontMetrics.getHeight();
    int strWidth = fontMetrics.stringWidth(str);
    // g.drawString(str, 2, height - (height - strHeight) / 2);
    g.drawString(str, 2, (int) (height * 0.8));
    // System.out.println(strWidth);
    if (strWidth + 2 < width && strHeight < height) {
      return image.getSubimage(0, 0, strWidth + 2, height);
    }
    System.err.println("Oops, development code name is too large. Cropping.");
    return image;
  }

  private static class LoginException extends RuntimeException {
    private static final long serialVersionUID = -3488730031206934808L;
  }
}
