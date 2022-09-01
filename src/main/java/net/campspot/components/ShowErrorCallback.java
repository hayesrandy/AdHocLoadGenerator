package net.campspot.components;

import okhttp3.Call;
import okhttp3.Callback;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.IOException;

public abstract class ShowErrorCallback implements Callback {
  private final String errorMessage;

  public ShowErrorCallback(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  @Override
  public void onFailure(@NotNull Call call, IOException e) {
    System.err.println(errorMessage);
    e.printStackTrace();
    JOptionPane.showMessageDialog(null, errorMessage, "Error", JOptionPane.ERROR_MESSAGE);
  }
}
