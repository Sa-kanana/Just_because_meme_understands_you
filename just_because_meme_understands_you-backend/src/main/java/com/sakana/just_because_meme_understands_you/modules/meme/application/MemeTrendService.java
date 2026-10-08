package com.sakana.just_because_meme_understands_you.modules.meme.application;

import com.sakana.just_because_meme_understands_you.api.user.vo.MemeTrendDayVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeTrendItemVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeTrendResponseVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeTrendTopicVO;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.MemeTrendRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class MemeTrendService {

    private static final int TOP_LIMIT = 20;
    private final MemeTrendRepository repository;

    public MemeTrendService(MemeTrendRepository repository) {
        this.repository = repository;
    }

    public MemeTrendResponseVO getTrends(int requestedDays) {
        int days = requestedDays == 30 ? 30 : 7;
        LocalDate endDate = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        LocalDate startDate = endDate.minusDays(days - 1L);
        List<MemeTrendItemVO> topMemes = repository.topMemes(startDate, endDate, TOP_LIMIT).stream()
                .map(row -> new MemeTrendItemVO(row.memeId(), row.name(), row.image(), row.views(),
                        row.likes(), row.comments(), row.score(), splitTags(row.tags())))
                .toList();
        List<MemeTrendRepository.DailyRow> recordedDays = repository.dailyTotals(startDate, endDate);
        Map<LocalDate, MemeTrendDayVO> dailyByDate = recordedDays.stream().collect(Collectors.toMap(
                MemeTrendRepository.DailyRow::date,
                row -> new MemeTrendDayVO(row.date(), row.views(), row.likes(), row.comments(), row.score()),
                (first, ignored) -> first));
        List<MemeTrendDayVO> daily = IntStream.range(0, days)
                .mapToObj(offset -> startDate.plusDays(offset))
                .map(date -> dailyByDate.getOrDefault(date, new MemeTrendDayVO(date, 0, 0, 0, 0)))
                .toList();
        return new MemeTrendResponseVO(days, startDate, endDate, topMemes, daily,
                weeklyTopic(endDate), recordedDays.size() >= 2);
    }

    private MemeTrendTopicVO weeklyTopic(LocalDate today) {
        WeekFields iso = WeekFields.ISO;
        int week = today.get(iso.weekOfWeekBasedYear());
        int year = today.get(iso.weekBasedYear());
        Optional<MemeTrendRepository.WeeklyTopicRow> saved = repository.findWeeklyTopic(year, week);
        if (saved.isPresent()) {
            return toTopicVO(saved.get(), year, week);
        }

        LocalDate fromDate = today.minusDays(6);
        MemeTrendRepository.TagTrendRow selected = repository.weeklyTagScores(fromDate, today)
                .stream().findFirst().orElse(null);
        boolean fallback = selected == null;
        if (fallback) {
            selected = repository.fallbackPopularPublicTag().orElse(null);
        }
        if (selected == null) {
            return null;
        }

        repository.insertWeeklyTopicIfAbsent(year, week, selected, fallback);
        MemeTrendRepository.WeeklyTopicRow finalTopic = repository.findWeeklyTopic(year, week)
                .orElseThrow(() -> new IllegalStateException("本周主题保存后无法读取"));
        return toTopicVO(finalTopic, year, week);
    }

    private MemeTrendTopicVO toTopicVO(MemeTrendRepository.WeeklyTopicRow topic, int year, int week) {
        return new MemeTrendTopicVO(topic.tagId(), topic.tagName(),
                year + " 年第 " + week + " 周", topic.fallback());
    }

    private List<String> splitTags(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split(",")).stream().map(String::trim).filter(tag -> !tag.isEmpty()).toList();
    }
}
