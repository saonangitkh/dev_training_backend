package com.devtraining.tickets.movie;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "movies")
public class Movie {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(columnDefinition = "text")
	private String description;

	@Column(name = "duration_minutes", nullable = false)
	private int durationMinutes;

	@Enumerated(EnumType.STRING)
	@Column(name = "age_rating", nullable = false, length = 10)
	private AgeRating ageRating;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Movie() {
		// for JPA
	}

	public Movie(String title, String description, int durationMinutes, AgeRating ageRating) {
		update(title, description, durationMinutes, ageRating);
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	/**
	 * Existing showtimes keep the end time computed when they were scheduled.
	 */
	public void update(String title, String description, int durationMinutes, AgeRating ageRating) {
		this.title = title;
		this.description = description;
		this.durationMinutes = durationMinutes;
		this.ageRating = ageRating;
	}

	public Duration getDuration() {
		return Duration.ofMinutes(durationMinutes);
	}

	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public int getDurationMinutes() {
		return durationMinutes;
	}

	public AgeRating getAgeRating() {
		return ageRating;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
