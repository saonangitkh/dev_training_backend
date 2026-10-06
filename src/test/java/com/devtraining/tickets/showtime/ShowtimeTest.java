package com.devtraining.tickets.showtime;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import com.devtraining.tickets.common.exception.ConflictException;
import com.devtraining.tickets.hall.Hall;
import com.devtraining.tickets.hall.SeatType;
import com.devtraining.tickets.movie.AgeRating;
import com.devtraining.tickets.movie.Movie;
import org.junit.jupiter.api.Test;

import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShowtimeTest {

	private static final Instant STARTS_AT = Instant.parse("2030-04-14T12:00:00Z");

	private final Movie movie = withId(new Movie("The Last Reel", null, 120, AgeRating.G), 1L);

	private final Hall hall = withId(new Hall("Hall 1"), 1L);

	private final Showtime showtime = new Showtime(movie, hall, STARTS_AT, STARTS_AT.plus(Duration.ofMinutes(135)),
			new BigDecimal("5.00"));

	@Test
	void seatPriceDependsOnSeatType() {
		assertThat(showtime.priceFor(SeatType.STANDARD)).isEqualByComparingTo("5.00");
		assertThat(showtime.priceFor(SeatType.VIP)).isEqualByComparingTo("7.50");
		assertThat(showtime.priceFor(SeatType.COUPLE)).isEqualByComparingTo("10.00");
	}

	@Test
	void salesCloseWhenTheShowtimeStarts() {
		showtime.checkOpenForSale(STARTS_AT.minusSeconds(1));
		assertThatThrownBy(() -> showtime.checkOpenForSale(STARTS_AT)).isInstanceOf(ConflictException.class)
			.hasMessageContaining("already started");
	}

	@Test
	void cancelledShowtimeIsNotForSale() {
		showtime.cancel();
		assertThatThrownBy(() -> showtime.checkOpenForSale(STARTS_AT.minusSeconds(60)))
			.isInstanceOf(ConflictException.class)
			.hasMessageContaining("cancelled");
		assertThatThrownBy(showtime::cancel).isInstanceOf(ConflictException.class);
	}

	@Test
	void startTimeCantMoveOnceSeatsAreSold() {
		Instant later = STARTS_AT.plus(Duration.ofDays(1));
		assertThatThrownBy(() -> showtime.update(movie, hall, later, later.plusSeconds(60), BigDecimal.TEN, true))
			.isInstanceOf(ConflictException.class)
			.hasMessageContaining("can't change");

		showtime.update(movie, hall, STARTS_AT, showtime.getEndsAt(), new BigDecimal("6.00"), true);
		assertThat(showtime.getBasePrice()).isEqualByComparingTo("6.00");

		showtime.update(movie, hall, later, later.plusSeconds(60), BigDecimal.TEN, false);
		assertThat(showtime.getStartsAt()).isEqualTo(later);
	}

	private static <T> T withId(T entity, Long id) {
		ReflectionTestUtils.setField(entity, "id", id);
		return entity;
	}

}
