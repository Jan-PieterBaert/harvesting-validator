package mu.semte.ch.harvesting.filtering.rest;

import lombok.extern.slf4j.Slf4j;
import mu.semte.ch.harvesting.filtering.lib.dto.Delta;
import mu.semte.ch.harvesting.filtering.lib.service.FilteringService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.util.Optional.ofNullable;
import static mu.semte.ch.harvesting.filtering.lib.Constants.*;

@RestController
@Slf4j
public class AppController {

    private final FilteringService filteringService;

    public AppController(FilteringService filteringService) {
        this.filteringService = filteringService;
    }

    @PostMapping("/delta")
    public ResponseEntity<Void> delta(@RequestBody Delta delta, HttpServletRequest request) {
      Map<String,String> muHeaders = new HashMap<>();
      ofNullable(request.getHeader(HEADER_MU_CALL_ID)).ifPresent(h -> muHeaders.put(HEADER_MU_CALL_ID, h));
      ofNullable(request.getHeader(HEADER_MU_SESSION_ID)).ifPresent(h -> muHeaders.put(HEADER_MU_SESSION_ID, h));
      muHeaders.put("HEADER_MU_AUTH_SUDO", "true");
      var entries = delta.getInsertsFor(SUBJECT_STATUS, STATUS_SCHEDULED);

        if (entries.isEmpty()) {
            log.error("Delta dit not contain potential tasks that are ready for filtering, awaiting the next batch!");
            return ResponseEntity.noContent().build();
        }

        // NOTE: we don't wait as we do not want to keep hold off the connection.
        entries.forEach(e ->
          filteringService.runFilterPipeline(e, muHeaders)
        );

        return ResponseEntity.ok().build();
    }
}
