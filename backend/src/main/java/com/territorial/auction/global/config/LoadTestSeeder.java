package com.territorial.auction.global.config;

import com.territorial.auction.global.security.jwt.JwtTokenProvider;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Profile("loadtest")
@RequiredArgsConstructor
public class LoadTestSeeder {

    private static final int USER_COUNT = 1_000;
    private static final int OCCUPIED_TERRITORY_COUNT = 1_500;
    private static final int ACTIVE_AUCTION_COUNT = 200;
    private static final int BUILDING_COUNT = 5_000;
    private static final int UNIT_COUNT = 10_000;
    private static final int CHAT_MESSAGE_COUNT = 100_000;

    private final JdbcTemplate jdbcTemplate;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${loadtest.token-output}")
    private String tokenOutput;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        if (countWhere("users", "username like 'loaduser%'") >= USER_COUNT) {
            log.info("부하 테스트 볼륨 시드가 이미 존재합니다. 토큰만 다시 생성합니다.");
            writeTokens();
            return;
        }

        seedUsers();
        seedUserResources();
        seedOccupiedTerritories();
        seedAuctions();
        seedBuildings();
        seedUnits();
        seedChatMessages();
        writeTokens();

        log.info(
                "부하 테스트 시드 완료. users={}, occupiedTerritories={}, activeAuctions={}, buildings={}, units={}, chatMessages={}",
                countWhere("users", "username like 'loaduser%'"),
                countWhere("territories", "status = 'OCCUPIED'"),
                countWhere("auctions", "settled = false and end_at > now()"),
                count("building_instances"),
                count("unit_instances"),
                count("chat_messages"));
    }

    private void seedUsers() {
        jdbcTemplate.update(
                """
                insert into users (username, email, password_hash, nickname, created_at, status, role)
                select 'loaduser' || n,
                       'loaduser' || n || '@test.local',
                       '$2a$10$loadtest.password.hash.not.used.for.login',
                       '부하유저' || n,
                       now(), 'ACTIVE', 'USER'
                from generate_series(1, ?) n
                """,
                USER_COUNT);
    }

    private void seedUserResources() {
        jdbcTemplate.update(
                """
                insert into wallets (user_id, available_ap, locked_ap, updated_at)
                select id, 100000000, 0, now() from users where username like 'loaduser%'
                """);
        jdbcTemplate.update(
                """
                insert into global_vaults (user_id, stored_gp, capacity, last_transfer_at)
                select id, 100000000, 200000000, null from users where username like 'loaduser%'
                """);
        jdbcTemplate.update(
                """
                insert into notification_settings
                    (user_id, is_outbid_enabled, is_auction_start_enabled, is_marketing_enabled, updated_at)
                select id, true, true, false, now() from users where username like 'loaduser%'
                """);
        jdbcTemplate.update(
                """
                insert into user_profiles (user_id, profile_image_url, updated_at)
                select id, null, now() from users where username like 'loaduser%'
                """);
        jdbcTemplate.update(
                """
                insert into home_islands
                    (user_id, level, grid_size, grade, island_grade_id, created_at, last_harvest_at)
                select u.id, 1, 10, 'D', ig.id, now(), now()
                from users u cross join island_grades ig
                where u.username like 'loaduser%' and ig.name = 'D'
                """);
    }

    private void seedOccupiedTerritories() {
        jdbcTemplate.update(
                """
                with candidates as (
                    select id, row_number() over (order by id) rn
                    from territories where status = 'IDLE' limit ?
                ), load_users as (
                    select id, row_number() over (order by id) rn
                    from users where username like 'loaduser%'
                )
                update territories t
                set owner_id = u.id,
                    status = 'OCCUPIED',
                    current_color = '#3366CC',
                    occupied_until = now() + interval '30 days',
                    protected_until = now() - interval '1 hour',
                    next_auction_at = null,
                    last_produced_at = now()
                from candidates c
                join load_users u on u.rn = ((c.rn - 1) % ?) + 1
                where t.id = c.id
                """,
                OCCUPIED_TERRITORY_COUNT, USER_COUNT);
    }

    private void seedAuctions() {
        Integer activeCount =
                jdbcTemplate.queryForObject(
                        "select count(*) from auctions where settled = false and end_at > now()",
                        Integer.class);
        int required = Math.max(0, ACTIVE_AUCTION_COUNT - activeCount);
        if (required == 0) return;

        jdbcTemplate.update(
                """
                with candidates as (
                    select id from territories where status = 'IDLE' order by id limit ?
                ), updated as (
                    update territories t set status = 'BIDDING', next_auction_at = null
                    from candidates c where t.id = c.id returning t.id
                )
                insert into auctions
                    (territory_id, current_bidder_id, current_price, start_at, end_at, max_extend_until, settled)
                select id, null, 1000, now(), now() + interval '2 hours',
                       now() + interval '3 hours', false
                from updated
                """,
                required);
    }

    private void seedBuildings() {
        jdbcTemplate.update(
                """
                insert into building_instances
                    (island_id, territory_id, building_type_id, user_id, pos_x, pos_y, hp, level,
                     zone, is_destroyed, stored_gp, stored_food, is_repairing)
                select hi.id, null, bt.id, null, 4, 4, bt.max_hp, 1, 1, false, 5000, 5000, false
                from home_islands hi cross join building_types bt
                join users u on u.id = hi.user_id
                where bt.name = 'CASTLE' and u.username like 'loaduser%'
                """);
        jdbcTemplate.update(
                """
                with occupied as (
                    select id, row_number() over (order by id) rn
                    from territories where status = 'OCCUPIED'
                )
                insert into building_instances
                    (island_id, territory_id, building_type_id, user_id, pos_x, pos_y, hp, level,
                     zone, is_destroyed, stored_gp, stored_food, is_repairing)
                select null, t.id, bt.id, null,
                       (n % 8) + 1, ((n / 8) % 8) + 1, bt.max_hp, 1, 2, false, 1000, 1000, false
                from generate_series(1, ?) n
                join occupied t on t.rn = ((n - 1) % ?) + 1
                cross join lateral (
                    select id, max_hp from building_types where name = 'STORAGE' limit 1
                ) bt
                """,
                BUILDING_COUNT - USER_COUNT, OCCUPIED_TERRITORY_COUNT);
    }

    private void seedUnits() {
        jdbcTemplate.update(
                """
                with load_users as (
                    select id, row_number() over (order by id) rn
                    from users where username like 'loaduser%'
                ), types as (
                    select id, row_number() over (order by id) rn, count(*) over () total
                    from unit_types
                )
                insert into unit_instances
                    (user_id, unit_type_id, quantity, level, home_island_id, home_territory_id,
                     deployed_territory_id, deployed_building_id, move_complete_at)
                select u.id, ut.id, 10, 1, hi.id, null, null, null, null
                from generate_series(1, ?) n
                join load_users u on u.rn = ((n - 1) % ?) + 1
                join home_islands hi on hi.user_id = u.id
                join types ut on ut.rn = ((n - 1) % ut.total) + 1
                """,
                UNIT_COUNT, USER_COUNT);
    }

    private void seedChatMessages() {
        jdbcTemplate.update(
                """
                with load_users as (
                    select id, row_number() over (order by id) rn
                    from users where username like 'loaduser%'
                ), first_room as (
                    select id from chat_rooms order by id limit 1
                )
                insert into chat_messages (room_id, sender_id, content, sent_at)
                select r.id, u.id, 'load test message ' || n,
                       now() - (n || ' seconds')::interval
                from generate_series(1, ?) n
                cross join first_room r
                join load_users u on u.rn = ((n - 1) % ?) + 1
                """,
                CHAT_MESSAGE_COUNT, USER_COUNT);
    }

    private void writeTokens() {
        List<Long> userIds =
                jdbcTemplate.queryForList(
                        "select id from users where username like 'loaduser%' order by id",
                        Long.class);
        Path output = Path.of(tokenOutput).toAbsolutePath().normalize();
        try {
            Files.createDirectories(output.getParent());
            try (BufferedWriter writer =
                    Files.newBufferedWriter(
                            output,
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING)) {
                writer.write("userId,token");
                writer.newLine();
                for (Long userId : userIds) {
                    writer.write(userId + "," + jwtTokenProvider.createAccessToken(userId, "USER"));
                    writer.newLine();
                }
            }
            log.info("부하 테스트 JWT feeder 생성 완료. path={}, count={}", output, userIds.size());
        } catch (IOException e) {
            throw new IllegalStateException("부하 테스트 JWT feeder 생성 실패: " + output, e);
        }
    }

    private int count(String table) {
        return countWhere(table, "true");
    }

    private int countWhere(String table, String predicate) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "select count(*) from " + table + " where " + predicate, Integer.class);
        return count != null ? count : 0;
    }
}
