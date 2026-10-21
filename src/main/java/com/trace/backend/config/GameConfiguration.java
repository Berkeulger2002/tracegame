package com.trace.backend.config;

import com.mongodb.client.model.IndexOptions;
import com.trace.backend.model.TraceGame;
import com.trace.backend.model.TraceGame.Difficulty;
import com.trace.backend.model.TraceGame.GameStatus;
import com.trace.backend.model.TraceGame.Option;
import com.trace.backend.model.TraceGame.Step;
import com.trace.backend.repository.TraceGameRepository;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Configuration
public class GameConfiguration {

    @Bean
    public Clock gameClock(
            @Value("${app.game.zone-id}") String zoneId
    ) {
        return Clock.system(ZoneId.of(zoneId));
    }

    @Bean
    public ApplicationRunner initializeGames(
            MongoTemplate mongoTemplate,
            TraceGameRepository repository,
            Clock gameClock,
            @Value("${app.seed.enabled:false}") boolean seedEnabled
    ) {
        return args -> {
            // Aynı yayın tarihine ikinci bir oyun eklenmesini engeller.
            mongoTemplate.getCollection("trace_game")
                    .createIndex(
                            new Document("publishDate", 1),
                            new IndexOptions()
                                    .unique(true)
                                    .name("unique_game_publish_date")
                    );

            if (!seedEnabled) {
                return;
            }

            String today = LocalDate.now(gameClock).toString();

            // Uygulama yeniden başlatılınca aynı oyunu tekrar ekleme.
            if (repository.existsByPublishDate(today)) {
                return;
            }

            Instant now = gameClock.instant();

            List<Step> steps = List.of(
                    new Step(
                            "step-1",
                            1,
                            List.of(
                                    new Option(
                                            "s1-a",
                                            "Kitap okurun evine teslim edildi."
                                    ),
                                    new Option(
                                            "s1-b",
                                            "Editör, yazılan metni düzenledi."
                                    ),
                                    new Option(
                                            "s1-c",
                                            "Henüz yazılmamış kitap ciltlendi."
                                    )
                            ),
                            "s1-b"
                    ),
                    new Step(
                            "step-2",
                            2,
                            List.of(
                                    new Option(
                                            "s2-a",
                                            "Düzenlenen metin basıldı ve ciltlendi."
                                    ),
                                    new Option(
                                            "s2-b",
                                            "Okur kitabın son sayfasını bitirdi."
                                    ),
                                    new Option(
                                            "s2-c",
                                            "Yazar henüz kitabın konusunu seçmedi."
                                    )
                            ),
                            "s2-a"
                    ),
                    new Step(
                            "step-3",
                            3,
                            List.of(
                                    new Option(
                                            "s3-a",
                                            "Yazılacak kitap için boş defter alındı."
                                    ),
                                    new Option(
                                            "s3-b",
                                            "Basılacak metnin ilk taslağı yazıldı."
                                    ),
                                    new Option(
                                            "s3-c",
                                            "Basılan kitap kitapçıya ulaştı ve satıldı."
                                    )
                            ),
                            "s3-c"
                    )
            );

            TraceGame game = new TraceGame(
                    null,
                    "Örnek oyun: Bir kitabın yolculuğu",
                    "tr",
                    today,
                    Difficulty.EASY,
                    GameStatus.PUBLISHED,
                    "Yazar kitabının metnini tamamladı.",
                    "Okur satın aldığı kitabı okumaya başladı.",
                    steps,
                    now,
                    now,
                    "local-seed",
                    "local-seed"
            );

            repository.insert(game);
        };
    }
}