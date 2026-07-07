ALTER TABLE `coupon`
    ADD COLUMN `issued_count` INT    NOT NULL DEFAULT 0 COMMENT '현재 발급된 수량 (낙관적 락용)',
    ADD COLUMN `version`      BIGINT NOT NULL DEFAULT 0 COMMENT '낙관적 락 버전';
