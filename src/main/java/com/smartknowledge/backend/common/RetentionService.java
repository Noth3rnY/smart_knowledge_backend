package com.smartknowledge.backend.common;

import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RetentionService {
    private final SqlMapper db;
    private final Clock clock;
    private final TransactionTemplate tx;
    public RetentionService(SqlMapper db,Clock clock,TransactionTemplate tx) { this.db=db; this.clock=clock; this.tx=tx; }
    @Scheduled(fixedDelayString="${app.cleanup-delay-ms:60000}",initialDelay=60000)
    public void cleanup() {
        var now=clock.instant();
        var users=db.list("SELECT user_id FROM backups WHERE expires_at<=? UNION SELECT user_id FROM note_versions WHERE expires_at<=? UNION SELECT user_id FROM ai_requests WHERE status='pending' AND expires_at<=?",now,now,now);
        for(var row:users) tx.executeWithoutResult(status->{
            String user=(String)row.get("userId"); db.lock(user);
            db.update("DELETE FROM backups WHERE user_id=? AND expires_at<=?",user,now);
            db.update("DELETE FROM note_versions WHERE user_id=? AND expires_at<=?",user,now);
            db.update("UPDATE ai_requests SET status='failed',error_code='AI_TIMEOUT',completed_at=? WHERE user_id=? AND status='pending' AND expires_at<=?",now,user,now);
        });
    }
}
