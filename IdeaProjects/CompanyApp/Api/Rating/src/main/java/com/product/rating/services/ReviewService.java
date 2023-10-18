package com.product.rating.services;

import com.product.rating.domain.Client;
import com.product.rating.domain.ReviewDomain;
import com.product.rating.repository.ClientRepository;
import com.product.rating.repository.ReviewRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ReviewService {

    private static final Logger logger = LogManager.getLogger(ReviewService.class);
    public static final Logger lowReviewsLogger = LogManager.getLogger("lowReviews");
    private final MongoTemplate mongoTemplate;
    private final ClientRepository clientRepository;
    private final ReviewRepository reviewRepository;

    @Autowired
    public ReviewService(ClientRepository clientRepository, MongoTemplate mongoTemplate, ReviewRepository reviewRepository) {
        this.clientRepository = clientRepository;
        this.mongoTemplate = mongoTemplate;
        this.reviewRepository = reviewRepository;
    }

    @Value("${collection.name}")
    private String collectionName; // Inject the collection name from application.properties

    @Value("${customlog.dateformat}")
    private String customLogDateFormat; // Inject the custom log date format

    @Value("${customlog.messageformat}")
    private String customLogMessageFormat; // Inject the custom log message format


    public void createCollection(String collectionName) {
        try {
            mongoTemplate.createCollection(this.collectionName); // Use the injected collection name
            logger.info("Created collection: {}", this.collectionName); // Use the injected collection name
        } catch (Exception e) {
            logger.error("Error creating collection {}: {}", this.collectionName, e.getMessage()); // Use the injected collection name
        }
    }

    public void deleteCollection(String collectionName) {
        try {
            mongoTemplate.dropCollection(collectionName);
            logger.info("Deleted collection: {}", collectionName);
        } catch (Exception e) {
            logger.error("Error deleting collection {}: {}", collectionName, e.getMessage());
        }
    }

    public List<ReviewDomain> viewAllReviews() {
        List<ReviewDomain> result = new ArrayList<>();
        Set<String> collectionNames = mongoTemplate.getCollectionNames();

        for (String collectionName : collectionNames) {
            try {
                logger.info("Fetching data from collection: {}", collectionName);
                Query query = new Query();
                List<ReviewDomain> collectionData = mongoTemplate.find(query, ReviewDomain.class, collectionName);
                result.addAll(collectionData);
            } catch (Exception e) {
                logger.error("Error fetching data from collection {}: {}", collectionName, e.getMessage());
            }
        }
        return result;
    }

    public List<ReviewDomain> viewReviewsInCollection(String collectionName) {
        try {
            logger.info("Fetching data from collection: {}", collectionName);
            Query query = new Query();
            List<ReviewDomain> collectionData = mongoTemplate.find(query, ReviewDomain.class, collectionName);
            return collectionData;
        } catch (Exception e) {
            logger.error("Error fetching data from collection {}: {}", collectionName, e.getMessage());
            return new ArrayList<>(); // Return an empty ArrayList instead of Collections.emptyList()
        }
    }


    private int reviewCounter = 1; // Initialize the counter

    public String addReview(ReviewDomain newReview) {
        try {
            List<Client> clients = clientRepository.findAll(Sort.by(Sort.Direction.DESC, "clientId"));
            List<ReviewDomain> reviews = reviewRepository.findAll(Sort.by(Sort.Direction.DESC, "reviewId"));

            if (!clients.isEmpty()) {
                String clientId = clients.get(0).getClientId(); // Fetch the most recent client ID
                newReview.setClientId(clientId);
            } else {
                System.out.println("No clients found. Setting clientId for the first review.");
                // You can handle this case as needed. You might want to throw an exception or log an error.
            }

            if (!reviews.isEmpty()) {
                int lastReviewId = Integer.parseInt(reviews.get(0).getReviewId());
                reviewCounter = lastReviewId + 1;
            } else {
                System.out.println("Reviews list is empty.");
                reviewCounter = 1; // Reset the review counter to 1 if the list is empty
            }

            // Increment the reviewId counter
            newReview.setReviewId(String.valueOf(reviewCounter));

            // Any additional logic or validation before adding the review can be added here

            // Assuming you have the necessary repository injected
            // Add the new review to the collection
            reviewRepository.save(newReview);

            return "Review added successfully with reviewId: " + newReview.getReviewId();
        } catch (Exception e) {
            throw new RuntimeException("Failed to add review: " + e.getMessage());
        }
    }



    public boolean deleteReviewById(String collectionName, String reviewId) {
        try {
            // Deleting from the first collection
            Query ratingQuery = new Query(Criteria.where("_id").is(reviewId)); // Change the field to "_id"
            ReviewDomain existingRating = mongoTemplate.findOne(ratingQuery, ReviewDomain.class, collectionName);

            if (existingRating != null) {
                mongoTemplate.remove(existingRating, collectionName);
            }

            // No need to delete from the second collection based on reviewId

            return true;
        } catch (Exception e) {
            lowReviewsLogger.error("Error deleting data by ID: {}", e.getMessage());
            return false;
        }
    }

    public ResponseEntity<ReviewDomain> updateReviewById(String collectionName, String id, ReviewDomain updatedReview) {
        try {
            Query query = new Query(Criteria.where("_id").is(id));
            ReviewDomain existingReview = mongoTemplate.findOne(query, ReviewDomain.class, collectionName);

            if (existingReview == null) {
                return ResponseEntity.notFound().build();
            }

            logger.info("Updating Review with ID: {}", id);

            // Add the existingReview to the historyList before updating it
            existingReview.addToHistory(existingReview);

            // Update the existingReview with the properties from updatedReview
            existingReview.setProductName(updatedReview.getProductName());
            existingReview.setFirstName(updatedReview.getFirstName());
            existingReview.setLastName(updatedReview.getLastName());
            existingReview.setZipCode(updatedReview.getZipCode());
            existingReview.setRateCode(updatedReview.getRateCode());
            existingReview.setComments(updatedReview.getComments());
            existingReview.setDateTime(updatedReview.getDateTime());

            // Save the updated existingReview to replace the existing document
            mongoTemplate.save(existingReview, collectionName);

            logger.info("Review with ID {} updated successfully");

            return ResponseEntity.ok(existingReview);
        } catch (Exception e) {
            logger.error("Error updating data by ID in collection {}: {}", collectionName, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    public List<ReviewDomain> getLatestReviews(int limit) {
        Query query = new Query()
                .with(Sort.by(Sort.Order.asc("_id")))
                .limit(limit);
        return mongoTemplate.find(query, ReviewDomain.class, collectionName);
    }
//datetime

    public void logLowRatingReview(ReviewDomain review) {
        // Extract the rating code from the review
        int ratingCode = review.getRateCode();

        // Check if the rating code is less than 3
        if (ratingCode < 3) {
            // Log the low-rated review
            lowReviewsLogger.info("Low-rated review with rating code: " + ratingCode);
        }
    }

    public List<ReviewDomain> findReviewsByRateCode(int rateCode) {
        Query query = new Query(Criteria.where("rateCode").is(rateCode));
        return mongoTemplate.find(query, ReviewDomain.class, collectionName); // Use the injected collection name
    }


}
