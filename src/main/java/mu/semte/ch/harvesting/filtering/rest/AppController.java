package mu.semte.ch.harvesting.filtering.rest;

import lombok.extern.slf4j.Slf4j;
import mu.semte.ch.harvesting.filtering.lib.dto.Delta;
import mu.semte.ch.harvesting.filtering.service.FilteringService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

import static mu.semte.ch.harvesting.filtering.Constants.STATUS_SCHEDULED;
import static mu.semte.ch.harvesting.filtering.Constants.SUBJECT_STATUS;

@RestController
@Slf4j
public class AppController {

  private final FilteringService filteringService;

  public AppController(FilteringService filteringService) {
    this.filteringService = filteringService;
  }

  @PostMapping("/delta")
  public ResponseEntity<Void> delta(@RequestBody List<Delta> deltas, HttpServletRequest request) {
    var entries = deltas.stream().findFirst().map(delta -> delta.getInsertsFor(SUBJECT_STATUS, STATUS_SCHEDULED))
    .orElseGet(List::of);

    if (entries.isEmpty()) {
      log.error("Delta dit not contain potential tasks that are ready for filtering, awaiting the next batch!");
      return ResponseEntity.noContent().build();

    }

    // NOTE: we don't wait as we do not want to keep hold off the connection.
    entries.forEach(filteringService::runFilterPipeline);

    return ResponseEntity.ok().build();
  }
}
