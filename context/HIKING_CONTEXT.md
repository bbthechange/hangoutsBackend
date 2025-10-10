# HIKING_CONTEXT.md - Hiking Trail Search Feature

## Overview

The Hiking feature allows users to search for hiking trails, view trail details, and get personalized trail suggestions. It integrates with OpenStreetMap (OSM) via the Overpass API for trail data and Open-Elevation API for elevation information.

## Architecture

### Multi-Source Data Integration

```
┌─────────────────┐
│ HikingController│
└────────┬────────┘
         │
┌────────▼────────┐
│  HikingService  │ ◄─── Orchestrates trail search & enrichment
└─────────┬───────┘
          │
          ├──────────────────┬─────────────────┐
          │                  │                 │
┌─────────▼─────────┐ ┌─────▼──────────┐ ┌───▼──────────┐
│ OverpassApiClient │ │OpenElevationCli│ │ Future: USGS │
│  (OSM trails)     │ │ (Elevation)    │ │  WA State    │
└───────────────────┘ └────────────────┘ └──────────────┘
```

### Data Flow

1. **User Request** → HikingController
2. **Search OSM** → OverpassApiClient queries OpenStreetMap
3. **Parse Trails** → Extract trail metadata, geometry, calculate distance
4. **Optional: Enrich Elevation** → OpenElevationClient adds elevation gain/loss
5. **Sort & Filter** → HikingService applies quality scoring and proximity sorting
6. **Response** → Return enriched trails to client

## Components

### HikingTrail (Model)

**Purpose**: Core data model representing a hiking trail.

**Key Fields**:
- `id`: Unique identifier (format: "osm-{type}-{osmId}")
- `name`: Trail name from OSM tags
- `location`: Starting point (lat/lng)
- `distanceKm`: Calculated from geometry using Haversine formula
- `elevationGainMeters`: From Open-Elevation API (optional)
- `difficulty`: Converted from OSM sac_scale tag
- `geometry`: List of coordinates (trail path)
- `source`: "OSM", "USGS", "WA_STATE" (future)
- `quality`: HIGH/MEDIUM/LOW based on field completeness

**Data Quality Assessment**:
```java
HIGH:   5+ fields populated (location, distance, name, difficulty, elevation, geometry)
MEDIUM: 3-4 fields populated
LOW:    <3 critical fields populated
```

### OverpassApiClient

**Purpose**: Query OpenStreetMap Overpass API for hiking trails.

**Key Methods**:
- `searchTrailsNearLocation(Location, radiusMeters)`: Find trails within radius
- `searchTrailsByName(String, Location)`: Search by trail name

**Overpass QL Query Example**:
```
[out:json];
(
  way["route"="hiking"](around:5000,47.7511,-121.7380);
  relation["route"="hiking"](around:5000,47.7511,-121.7380);
);
out geom;
```

**OSM Tags Used**:
- `route=hiking`: Identifies hiking trails
- `name`: Trail name
- `sac_scale`: Difficulty rating (hiking, mountain_hiking, alpine_hiking, etc.)
- `trail_visibility`: Path visibility
- `surface`: Trail surface type
- `operator`: Managing organization

**sac_scale to Difficulty Mapping**:
```
hiking                        → Easy
mountain_hiking               → Moderate
demanding_mountain_hiking     → Hard
alpine_hiking                 → Hard
demanding_alpine_hiking       → Very Hard
difficult_alpine_hiking       → Very Hard
```

**Distance Calculation**:
- Sums Haversine distance between each consecutive coordinate pair
- Formula: `d = 2R * arcsin(sqrt(haversin(Δlat) + cos(lat1)*cos(lat2)*haversin(Δlon)))`
- R = 6371 km (Earth radius)

### OpenElevationClient

**Purpose**: Fetch elevation data and calculate elevation gain/loss.

**Key Methods**:
- `enrichTrailWithElevation(HikingTrail)`: Add elevation gain/loss to trail

**Elevation Calculation Strategy**:
1. **Sample Coordinates**: Reduce API calls by sampling max 20 points evenly spaced
2. **Batch Requests**: Query up to 100 locations per API call
3. **Calculate Gain/Loss**: Sum positive deltas (gain) and negative deltas (loss)
4. **Update Geometry**: Assign elevations to all coordinates (using closest sampled point)

**API Endpoint**: `https://api.open-elevation.com/api/v1/lookup`

**Rate Limiting**: 100ms delay between batches to respect API limits

**Example Response**:
```json
{
  "results": [
    {"latitude": 47.7511, "longitude": -121.7380, "elevation": 1200},
    {"latitude": 47.7520, "longitude": -121.7390, "elevation": 1250}
  ]
}
```

**Elevation Stats Calculation**:
```
For each consecutive pair of points:
  if (elevation[i+1] > elevation[i]):
    totalGain += (elevation[i+1] - elevation[i])
  else:
    totalLoss += (elevation[i] - elevation[i+1])
```

### HikingService

**Purpose**: Orchestrate trail search and enrichment with business logic.

**Key Methods**:

1. **`searchTrailsByName(name, location, includeElevation)`**
   - Queries OSM by trail name
   - Optionally enriches with elevation data
   - Sorts by proximity if location given, else by quality
   - **Use Case**: "Find Mount Rainier Wonderland Trail"

2. **`searchTrailsNearLocation(location, radiusKm, includeElevation)`**
   - Finds all trails within radius
   - Sorts by distance from center point
   - **Use Case**: "What trails are near me?"

3. **`suggestTrails(location, radiusKm, difficulty, maxDistanceKm)`**
   - Filters by difficulty and trail length
   - Returns top 20 high-quality trails
   - **Use Case**: "Show me moderate hikes under 10km"

4. **`getTrailById(trailId)`**
   - Get full trail details (NOT YET IMPLEMENTED - requires caching)
   - Always includes elevation for detail view

**Sorting Strategies**:
- **By Proximity**: Haversine distance from user location
- **By Quality**: DataQuality enum (HIGH > MEDIUM > LOW)

### HikingController

**Purpose**: REST API endpoints for hiking trail search.

**Endpoints**:

| Method | Path | Description |
|--------|------|-------------|
| GET | `/hiking/trails/search` | Search trails by name |
| GET | `/hiking/trails/nearby` | Find trails near location |
| GET | `/hiking/trails/suggest` | Get personalized suggestions |
| GET | `/hiking/trails/{trailId}` | Get trail details (NYI) |
| GET | `/hiking/health` | Health check |

**Authentication**: Currently **PUBLIC** (no JWT required)
- Consider adding JWT for future features (saved trails, reviews)

**Example Requests**:

**Search by Name**:
```bash
GET /hiking/trails/search?name=Wonderland&latitude=46.8523&longitude=-121.7603&includeElevation=true
```

**Nearby Trails**:
```bash
GET /hiking/trails/nearby?latitude=47.7511&longitude=-121.7380&radiusKm=10&includeElevation=false
```

**Trail Suggestions**:
```bash
GET /hiking/trails/suggest?latitude=47.7511&longitude=-121.7380&radiusKm=15&difficulty=Moderate&maxDistanceKm=12
```

## API Response Format

**HikingTrail Response Example**:
```json
{
  "id": "osm-way-123456789",
  "name": "Mount Rainier Wonderland Trail",
  "location": {
    "latitude": 46.8523,
    "longitude": -121.7603
  },
  "distanceKm": 150.5,
  "elevationGainMeters": 6700,
  "elevationLossMeters": 6700,
  "difficulty": "Hard",
  "trailType": "hiking",
  "region": "Mount Rainier National Park",
  "source": "OSM",
  "externalId": "way/123456789",
  "externalLink": "https://www.openstreetmap.org/way/123456789",
  "geometry": [
    {"latitude": 46.8523, "longitude": -121.7603, "elevation": 1200},
    {"latitude": 46.8530, "longitude": -121.7610, "elevation": 1250}
  ],
  "metadata": {
    "sac_scale": "mountain_hiking",
    "surface": "ground",
    "operator": "National Park Service"
  },
  "quality": "HIGH",
  "lastUpdated": 1704931200000
}
```

## Data Sources

### Primary: OpenStreetMap (OSM)

**Why OSM**:
- ✅ Free, global coverage
- ✅ Rich trail metadata (names, difficulty, type)
- ✅ Trail geometry for distance calculation
- ✅ Community-maintained, constantly updated
- ✅ No API key required

**Limitations**:
- ❌ Elevation data rarely populated
- ❌ Data quality varies by region
- ❌ No trail ratings/recommendations
- ❌ Unnamed trails common in some areas

**Best Coverage**: Popular hiking areas (Washington State, California, Colorado, etc.)

### Secondary: Open-Elevation

**Why Open-Elevation**:
- ✅ Free, no API key required
- ✅ Global coverage using SRTM data
- ✅ Reliable elevation data

**Limitations**:
- ❌ Slower (requires separate API calls)
- ❌ Rate limited (need delays between batches)
- ❌ Not real-time (may lag behind terrain changes)

**Best Practice**: Use `includeElevation=false` for search, `true` for detail view

### Future: USGS National Digital Trails

**Potential Use**: Supplement OSM with official US trail data
- Federal, state, and local trails
- WFS (Web Feature Service) endpoint
- GeoJSON output

**Status**: Researched but not yet implemented

### Future: State APIs (WA, CA, TX, TN)

**Potential Use**: Fill gaps in OSM coverage for specific states
- Washington State RCO Trails Database
- California State Parks GIS
- ArcGIS REST API pattern

**Status**: Researched but not yet implemented

## Implementation Patterns

### Error Handling

**Strategy**: Graceful degradation
- OSM query fails → Return empty list, log error
- Elevation API fails → Return trails without elevation
- Invalid coordinates → Return 400 Bad Request

**Logging**: All external API calls logged at INFO level with timing

### Performance Optimization

**Current**:
- Coordinate sampling reduces Open-Elevation API calls (20 samples max)
- 100ms delay between elevation batches for rate limiting

**Future Improvements**:
1. **Caching Layer**: DynamoDB table for frequently searched trails
2. **Async Processing**: Enrich elevation data asynchronously
3. **CDN**: Cache static trail data at edge locations

### Testing Strategy

**Unit Tests Required** (per CLAUDE.md):
- OverpassApiClient: Mock RestTemplate, test query building and parsing
- OpenElevationClient: Mock elevation responses, test calculation logic
- HikingService: Mock clients, test sorting and filtering
- HikingController: Mock service, test endpoint behavior

**Integration Tests**:
- Live OSM Overpass API call (Washington State trails)
- Live Open-Elevation API call (sample coordinates)

**Test Data**:
- Mount Rainier area: `lat=46.8523, lng=-121.7603`
- Seattle area: `lat=47.6062, lng=-122.3321`

## Known Issues & Future Work

### Known Issues

1. **`getTrailById()` Not Implemented**
   - Requires caching layer to avoid re-searching
   - Currently returns null with warning log

2. **No Trail Recommendations**
   - No API provides "trails people liked"
   - Future: Build user review/rating system (see STATE_TRAILS_API_RESEARCH.md)

3. **OSM Data Completeness Varies**
   - Some regions have excellent data, others sparse
   - Mitigated by data quality scoring

### Future Enhancements

#### Phase 1: Caching (High Priority)
```java
@Entity
@DynamoDbBean
public class CachedTrail {
    private String id;
    private HikingTrail trailData;
    private Long lastUpdated;
    private Integer searchCount;
}
```

#### Phase 2: User Reviews (Competitive Advantage)
```java
@Entity
public class TrailReview {
    private String trailId;
    private String userId;
    private Integer rating;         // 1-5 stars
    private String difficulty;      // User-submitted
    private String conditions;      // Recent trail conditions
    private List<String> photos;
    private LocalDate hikedDate;
}
```

#### Phase 3: Multi-Source Aggregation
- Integrate USGS National Digital Trails
- Add Washington State Trails Database
- Merge duplicate trails from different sources

#### Phase 4: Recommendations Algorithm
```java
public List<Trail> recommendTrails(String userId) {
    // Collaborative filtering: users who liked X also liked Y
    // Content-based: similar distance/elevation/difficulty
    // Social: trails friends have liked
}
```

## Integration with Hangouts

### Use Cases

1. **Creating Hiking Hangout**:
   ```
   User creates hangout → Search trails → Select trail → Auto-populate:
     - Hangout name: Trail name
     - Location: Trailhead coordinates
     - Description: Distance, elevation, difficulty
   ```

2. **Suggesting Hangout Locations**:
   ```
   User starts hangout → App suggests nearby trails → User picks one
   ```

3. **Trail-Specific Hangouts**:
   - Link hangout to trail ID
   - Display trail map in hangout detail
   - Show which friends have hiked this trail

### Future Integration

**Saved Places + Trails**:
```java
@Entity
public class SavedPlace {
    private String userId;
    private String placeId;
    private String placeType;  // "TRAIL", "RESTAURANT", "PARK"
    private String externalId; // Trail ID if type=TRAIL
}
```

**Hangout Events + Trails**:
```java
public class HangoutEvent {
    private String hangoutId;
    private Location location;
    private String trailId;    // NEW: Link to hiking trail
}
```

## Security Considerations

**Current**: No authentication required for hiking endpoints
- Public trail data (OSM is open data)
- No user-specific information returned

**Future**: Add JWT authentication for:
- Saving favorite trails
- Submitting trail reviews
- Seeing friends' trail activity

## External Dependencies

**Spring Boot Beans Used**:
- `@Qualifier("externalRestTemplate")`: HTTP client with timeouts configured
- `ObjectMapper`: JSON parsing for API responses

**Configuration**:
```properties
# In application.properties
external.parser.connection-timeout=5000
external.parser.read-timeout=10000
```

**No new dependencies required** - uses existing HTTP client infrastructure

## Testing Instructions

### Manual Testing

**Start application**:
```bash
./gradlew bootRun
```

**Test search by name** (Mount Rainier area):
```bash
curl "http://localhost:8080/hiking/trails/search?name=Wonderland&latitude=46.8523&longitude=-121.7603"
```

**Test nearby search** (Seattle area):
```bash
curl "http://localhost:8080/hiking/trails/nearby?latitude=47.6062&longitude=-122.3321&radiusKm=10"
```

**Test suggestions**:
```bash
curl "http://localhost:8080/hiking/trails/suggest?latitude=47.6062&longitude=-122.3321&radiusKm=15&difficulty=Easy&maxDistanceKm=5"
```

**Test with elevation** (slower):
```bash
curl "http://localhost:8080/hiking/trails/search?name=Rattlesnake&latitude=47.4354&longitude=-121.7715&includeElevation=true"
```

### Unit Test Coverage Required

Per CLAUDE.md MANDATORY TESTING requirement:

- [ ] OverpassApiClientTest
  - Test query building for location search
  - Test query building for name search
  - Test trail parsing from OSM JSON
  - Test geometry extraction
  - Test distance calculation
  - Test difficulty conversion

- [ ] OpenElevationClientTest
  - Test coordinate sampling
  - Test elevation response parsing
  - Test elevation gain/loss calculation
  - Test batch processing

- [ ] HikingServiceTest
  - Test search by name (with/without location)
  - Test search by location
  - Test trail suggestions with filters
  - Test sorting by proximity
  - Test sorting by quality

- [ ] HikingControllerTest
  - Test all endpoint request/response handling
  - Test parameter validation
  - Test error handling

## Related Documentation

- `/docs/HIKING_API_COMPARISON.md`: Research comparing 9 trail APIs
- `/docs/STATE_TRAILS_API_RESEARCH.md`: State-level APIs and recommendations feature
- `/docs/RIDB_API_ANALYSIS.md`: Why RIDB was not chosen
- `/context/ATTRIBUTE_ADDITION_GUIDE.md`: Pattern for adding new features
- `CLAUDE.md`: TDD requirements and testing protocol

## Changelog

- **2025-01-10**: Initial implementation - OSM + Open-Elevation integration
- **Future**: Add caching layer (DynamoDB)
- **Future**: Add user review/rating system
- **Future**: Integrate USGS and state APIs

---

**Maintained by**: HangoutsBackend team
**Last Updated**: 2025-01-10
