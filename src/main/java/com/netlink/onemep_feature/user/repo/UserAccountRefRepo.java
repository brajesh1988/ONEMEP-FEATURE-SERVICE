package com.netlink.onemep_feature.user.repo;

import com.netlink.onemep_feature.user.model.UserAccountRef;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Read-only access to identity-owned users referenced by project leads/members. */
@Repository
public interface UserAccountRefRepo extends JpaRepository<UserAccountRef, Long> {

  List<UserAccountRef> findByIdIn(Collection<Long> ids);
}
