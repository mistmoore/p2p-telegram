package dev.p2p.infrastructure.jackett;

import dev.p2p.application.port.out.TorrentSearchPort;
import dev.p2p.domain.TorrentResult;
import dev.p2p.infrastructure.config.ApplicationConfiguration;
import dev.p2p.infrastructure.http.HttpGateway;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;

@ApplicationScoped
public class JackettTorrentSearch implements TorrentSearchPort {
    private final HttpGateway http;
    private final ApplicationConfiguration configuration;

    @Inject
    public JackettTorrentSearch(HttpGateway http, ApplicationConfiguration configuration) {
        this.http = http;
        this.configuration = configuration;
    }

    @Override
    public List<TorrentResult> search(String query) throws Exception {
        String endpoint = stripTrailingSlash(configuration.jackettUrl()) + "/api/v2.0/indexers/all/results/torznab/api?t=search&q=" + HttpGateway.encode(query) + "&apikey=" + HttpGateway.encode(configuration.jackettApiKey());
        var document = secureFactory().newDocumentBuilder().parse(new ByteArrayInputStream(http.get(endpoint).body().getBytes(StandardCharsets.UTF_8)));
        var items = document.getElementsByTagName("item");
        var results = new LinkedHashMap<String, TorrentResult>();
        for (int i = 0; i < items.getLength() && results.size() < 15; i++) {
            var item = (org.w3c.dom.Element) items.item(i);
            var titleNodes = item.getElementsByTagName("title");
            var linkNodes = item.getElementsByTagName("link");
            if (titleNodes.getLength() == 0 || linkNodes.getLength() == 0) continue;
            String title = normalizeTitle(titleNodes.item(0).getTextContent());
            String link = normalizeLink(linkNodes.item(0).getTextContent());
            if (title != null && link != null) results.putIfAbsent(link, new TorrentResult(title, link));
        }
        return List.copyOf(results.values());
    }

    private static DocumentBuilderFactory secureFactory() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    private static String normalizeTitle(String value) {
        String title = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return title.isEmpty() ? null : title;
    }

    private static String normalizeLink(String value) {
        try {
            var uri = URI.create(value.trim()).normalize();
            String scheme = uri.getScheme();
            if (scheme == null || !scheme.equalsIgnoreCase("magnet")) return null;
            return uri.toString();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String stripTrailingSlash(String value) { return value.endsWith("/") ? value.substring(0, value.length() - 1) : value; }
}
