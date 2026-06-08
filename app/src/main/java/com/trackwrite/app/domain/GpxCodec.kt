package com.trackwrite.app.domain

import org.w3c.dom.Element
import java.io.StringReader
import java.io.StringWriter
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.xml.sax.InputSource

class GpxCodec {
    fun encode(track: Track): String {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        val gpx = document.createElement("gpx").apply {
            setAttribute("version", "1.1")
            setAttribute("creator", "TrackWrite")
            setAttribute("xmlns", "http://www.topografix.com/GPX/1/1")
        }
        document.appendChild(gpx)

        val trk = document.createElement("trk")
        gpx.appendChild(trk)

        val name = document.createElement("name")
        name.textContent = track.name
        trk.appendChild(name)

        val segment = document.createElement("trkseg")
        trk.appendChild(segment)

        track.points.forEach { point ->
            val trkpt = document.createElement("trkpt").apply {
                setAttribute("lat", point.position.latitude.toString())
                setAttribute("lon", point.position.longitude.toString())
            }
            point.position.altitudeMeters?.let { altitude ->
                trkpt.appendChild(document.createElement("ele").apply { textContent = altitude.toString() })
            }
            trkpt.appendChild(document.createElement("time").apply { textContent = point.recordedAt.toString() })
            segment.appendChild(trkpt)
        }

        return StringWriter().use { writer ->
            TransformerFactory.newInstance().newTransformer().apply {
                setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
                setOutputProperty(OutputKeys.INDENT, "yes")
            }.transform(DOMSource(document), StreamResult(writer))
            writer.toString()
        }
    }

    fun decode(id: String, xml: String, maxTrackPoints: Int = Int.MAX_VALUE): Track {
        require(maxTrackPoints > 0) { "GPX track point limit must be positive." }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            isExpandEntityReferences = false
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val name = document.getElementsByTagNameNS("*", "name")
            .item(0)
            ?.textContent
            ?.takeIf { it.isNotBlank() }
            ?: "Imported track"

        val points = document.getElementsByTagNameNS("*", "trkpt").let { nodes ->
            require(nodes.length <= maxTrackPoints) {
                "GPX file has more than $maxTrackPoints track points."
            }
            List(nodes.length) { index ->
                val element = nodes.item(index) as Element
                TrackPoint(
                    position = GeoPoint(
                        latitude = element.getAttribute("lat").toDouble(),
                        longitude = element.getAttribute("lon").toDouble(),
                        altitudeMeters = element.childText("ele")?.toDouble(),
                    ),
                    recordedAt = Instant.parse(
                        requireNotNull(element.childText("time")) { "GPX point is missing time." },
                    ),
                )
            }
        }

        return Track(id = id, name = name, points = points.sortedBy { it.recordedAt })
    }

    private fun Element.childText(localName: String): String? {
        val nodes = getElementsByTagNameNS("*", localName)
        return nodes.item(0)?.textContent?.takeIf { it.isNotBlank() }
    }
}
