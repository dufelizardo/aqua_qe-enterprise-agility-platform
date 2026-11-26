package br.com.aquaqe.platform.activity;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.aquaqe.platform.activity.ActivityService.NewsItem;
import br.com.aquaqe.platform.activity.ActivityService.RecentItem;

/** Home (#83): itens abertos por último e novidades das unidades que a pessoa lê. */
@RestController
@RequestMapping("/api/v1/me")
class ActivityController {

	private final ActivityService service;

	ActivityController(ActivityService service) {
		this.service = service;
	}

	@GetMapping("/recent")
	Items<RecentItem> recent() {
		return new Items<>(service.recent());
	}

	@PostMapping("/recent")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void visit(@RequestBody Map<String, Object> body) {
		service.visit(body);
	}

	@GetMapping("/news")
	Items<NewsItem> news() {
		return new Items<>(service.news());
	}

	record Items<T>(List<T> items) {
	}
}
