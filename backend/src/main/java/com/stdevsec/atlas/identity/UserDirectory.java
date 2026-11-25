package com.stdevsec.atlas.identity;

import com.stdevsec.atlas.identity.internal.AppUser;
import com.stdevsec.atlas.identity.internal.AppUserRepository;
import com.stdevsec.atlas.identity.internal.PasswordPolicy;
import com.stdevsec.atlas.shared.ConflictException;
import com.stdevsec.atlas.shared.Ids;
import com.stdevsec.atlas.shared.NotFoundException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public API of the identity module for creating and looking up users. */
@Service
public class UserDirectory {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    UserDirectory(AppUserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Transactional
    public UserSummary createUser(String username, String displayName, String rawPassword, String locale,
            String timeZone, boolean admin) {
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new ConflictException("Username already exists");
        }
        PasswordPolicy.check(username, rawPassword);
        AppUser user = new AppUser(Ids.newId(), username, displayName, encoder.encode(rawPassword), locale,
                timeZone, admin);
        users.save(user);
        return user.toSummary();
    }

    @Transactional(readOnly = true)
    public UserSummary get(UUID id) {
        return users.findById(id).map(AppUser::toSummary).orElseThrow(() -> new NotFoundException("User"));
    }

    @Transactional(readOnly = true)
    public Optional<UserSummary> findByUsername(String username) {
        return users.findByUsernameIgnoreCase(username).map(AppUser::toSummary);
    }

    @Transactional(readOnly = true)
    public List<UserSummary> findAll(Collection<UUID> ids) {
        return users.findAllById(ids).stream().map(AppUser::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public boolean anyAdminExists() {
        return users.existsByAdminTrue();
    }
}
