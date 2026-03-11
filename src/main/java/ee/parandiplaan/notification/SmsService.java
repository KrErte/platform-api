package ee.parandiplaan.notification;

public interface SmsService {

    /**
     * Send an SMS message to the given phone number.
     *
     * @param to      phone number in E.164 format (e.g. +3725xxxxxxx)
     * @param message text message content
     */
    void sendSms(String to, String message);
}
