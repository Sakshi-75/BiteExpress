// Read-only mongosh script. Run against the same database used by the API.
const restaurants = db.getCollection('restaurants');
const currency = /^(?:INR|Indian Rupees\(Rs\.\))$/i;
const combined = {
  $and: [
    { 'address.city': /^Gurgaon$/i },
    { cuisines: /^North Indian$/i },
    { 'rating.average': { $gte: 3 } },
    { 'pricing.currency': currency },
    { 'pricing.averageCostForTwo': { $lte: 2000 } },
  ],
};
const natural = {
  $and: [
    { 'address.city': /^(?:Gurgaon|Gurugram)$/i },
    { cuisines: /^North Indian$/i },
    { 'rating.average': { $gte: 0 } },
    { 'pricing.currency': currency },
    { 'pricing.averageCostForTwo': { $lte: 2000 } },
  ],
};
const geoNear = {
  near: { type: 'Point', coordinates: [77.0639, 28.4674] },
  distanceField: 'distanceMeters',
  spherical: true,
  key: 'location',
  maxDistance: 5000,
};

function findFirst(value, predicate) {
  if (!value || typeof value !== 'object') return null;
  if (predicate(value)) return value;
  for (const child of Object.values(value)) {
    const found = findFirst(child, predicate);
    if (found) return found;
  }
  return null;
}

function collect(value, key, result = []) {
  if (!value || typeof value !== 'object') return result;
  if (Object.hasOwn(value, key)) result.push(value[key]);
  for (const child of Object.values(value)) collect(child, key, result);
  return result;
}

function report(name, explain) {
  const winning = findFirst(explain, (part) => Object.hasOwn(part, 'winningPlan'))?.winningPlan;
  const stats = findFirst(explain, (part) =>
    Object.hasOwn(part, 'totalDocsExamined') && Object.hasOwn(part, 'totalKeysExamined'));
  print(JSON.stringify({
    query: name,
    winningStage: winning?.queryPlan?.stage ?? winning?.stage ?? null,
    winningStages: [...new Set(collect(winning, 'stage'))],
    indexesUsed: [...new Set(collect(winning, 'indexName'))],
    keysExamined: stats?.totalKeysExamined ?? null,
    documentsExamined: stats?.totalDocsExamined ?? null,
    executionTimeMs: stats?.executionTimeMillis ?? null,
    returned: stats?.nReturned ?? null,
  }));
}

print(JSON.stringify({ database: db.getName(), documents: restaurants.countDocuments({}),
  indexes: restaurants.getIndexes().map((index) => ({ name: index.name, key: index.key })),
  combinedMatches: restaurants.countDocuments(combined),
  naturalMatches: restaurants.countDocuments(natural),
  geoMatches: restaurants.aggregate([
    { $geoNear: geoNear }, { $match: combined }, { $count: 'total' },
  ]).toArray()[0]?.total ?? 0,
}));
report('listing-count', restaurants.explain('executionStats').aggregate([{ $count: 'total' }]));
report('listing-page', restaurants.find({}).sort({ name: 1, _id: 1 }).limit(20).explain('executionStats'));
report('combined-count', restaurants.explain('executionStats').aggregate([{ $match: combined }, { $count: 'total' }]));
report('combined-page', restaurants.find(combined).sort({ name: 1, _id: 1 }).limit(20).explain('executionStats'));
report('geo-count', restaurants.explain('executionStats').aggregate([
  { $geoNear: geoNear }, { $match: combined }, { $count: 'total' },
]));
report('geo-page', restaurants.explain('executionStats').aggregate([
  { $geoNear: geoNear }, { $match: combined },
  { $sort: { distanceMeters: 1, _id: 1 } }, { $skip: 0 }, { $limit: 20 },
]));
report('natural-count', restaurants.explain('executionStats').aggregate([{ $match: natural }, { $count: 'total' }]));
