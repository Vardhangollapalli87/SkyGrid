package com.skygrid.mission.mission;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class AirspaceImportRunner implements ApplicationRunner {
    private final AirspaceSnapshotImporter importer;
    public AirspaceImportRunner(AirspaceSnapshotImporter importer) { this.importer = importer; }

    @Override public void run(ApplicationArguments args) throws Exception {
        if (!args.containsOption("airspace-import-snapshots")) return;
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        if (root.endsWith(Path.of("services", "mission-service"))) root = root.getParent().getParent();
        for (AirspaceSnapshotImporter.ImportSummary summary : importer.importRepositorySnapshots(root)) {
            var c = summary.counts();
            System.out.printf("Airspace import complete country=%s dataset=%s status=%s parsed=%d inserted=%d updated=%d skipped=%d spatial=%d nonSpatial=%d rejected=%d duplicates=%d failed=%d%n",
                    summary.country(), summary.dataset(), summary.status(), c.parsed(), c.inserted(), c.updated(),
                    c.skipped(), c.spatial(), c.nonSpatial(), c.rejected(), c.duplicates(), c.failed());
        }
    }
}
