package Depolita.domain.deployment.entity;

/** 배포를 시작시킨 원인 */
public enum DeployTrigger {
    /** 앱을 처음 만들 때 */
    INITIAL,
    /** 대시보드에서 재배포 버튼 */
    MANUAL,
    /** GitHub push Webhook */
    WEBHOOK
}
