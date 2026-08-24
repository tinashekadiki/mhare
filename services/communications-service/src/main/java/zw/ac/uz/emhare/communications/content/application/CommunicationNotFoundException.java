package zw.ac.uz.emhare.communications.content.application;

/** Indicates a Communications resource is absent or not publicly visible. @author Tinashe K */
public class CommunicationNotFoundException extends RuntimeException {

  public CommunicationNotFoundException(String message) {
    super(message);
  }
}
