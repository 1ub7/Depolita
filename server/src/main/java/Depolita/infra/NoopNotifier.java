package Depolita.infra;

import Depolita.domain.app.port.Notifier;

/** 알림 채널이 설정되지 않았을 때 쓴다 */
public class NoopNotifier implements Notifier {
    @Override
    public void send(String message) {
    }
}
