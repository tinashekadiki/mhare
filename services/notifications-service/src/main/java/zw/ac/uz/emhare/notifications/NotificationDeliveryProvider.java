package zw.ac.uz.emhare.notifications;

import java.util.Map;

/** @author Tinashe K */
public interface NotificationDeliveryProvider {
    String code();
    DeliveryResult deliver(NotificationRequest request);
    record DeliveryResult(boolean sent,boolean retryable,String providerMessageId,String errorCode,String errorMessage,Map<String,Object> metadata) {
        public static DeliveryResult sent(String id,Map<String,Object> metadata){return new DeliveryResult(true,false,id,null,null,metadata);}
        public static DeliveryResult failed(boolean retryable,String code,String message,Map<String,Object> metadata){return new DeliveryResult(false,retryable,null,code,message,metadata);}
    }
}
