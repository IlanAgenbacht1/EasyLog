package com.easylog.android.data.repository

import android.util.Log
import com.easylog.android.data.local.dao.CustomerDao
import com.easylog.android.data.local.entity.CustomerEntity
import com.easylog.android.domain.model.Customer
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

sealed class CustomerResult<out T> {
    data class Success<T>(val data: T) : CustomerResult<T>()
    data class Error(val message: String) : CustomerResult<Nothing>()
}

class CustomerRepository(
    private val customerDao: CustomerDao
) {
    private val firestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "CustomerRepository"
        private const val CUSTOMERS_COLLECTION = "customers"
    }

    /**
     * Fetch customers from Firestore and cache locally.
     * Firestore SDK handles offline automatically.
     */
    suspend fun fetchCustomers(): CustomerResult<List<Customer>> {
        return try {
            val query = firestore.collection(CUSTOMERS_COLLECTION)
                .whereEqualTo("isActive", true)

            val snapshot = try {
                query.get(Source.DEFAULT).await()
            } catch (cacheError: Exception) {
                Log.d(TAG, "Cache miss for customers, fetching from server")
                query.get(Source.CACHE).await()
            }

            val customers = snapshot.documents.mapNotNull { doc ->
                try {
                    val entity = CustomerEntity(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        address = doc.getString("address"),
                        phone = doc.getString("contactPhone"),
                        email = doc.getString("contactEmail"),
                        contactPerson = doc.getString("contactName"),
                        isActive = doc.getBoolean("isActive") ?: true,
                        createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis(),
                        updatedAt = doc.getTimestamp("updatedAt")?.toDate()?.time ?: System.currentTimeMillis(),
                        isSynced = true
                    )
                    // Cache locally
                    customerDao.insertCustomer(entity)
                    entity.toDomainModel()
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing customer ${doc.id}", e)
                    null
                }
            }

            CustomerResult.Success(customers)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching customers", e)
            // Firestore SDK returns cached data when offline, so this error
            // typically means no cached data exists
            CustomerResult.Error("Failed to fetch customers: ${e.localizedMessage}")
        }
    }

    /**
     * Create a new customer in Firestore.
     */
    suspend fun createCustomer(
        name: String,
        address: String?,
        phone: String?,
        email: String?,
        contactPerson: String?,
        createdBy: String
    ): CustomerResult<Customer> {
        return try {
            val now = com.google.firebase.Timestamp.now()
            val customerData = hashMapOf(
                "name" to name,
                "address" to address,
                "contactPhone" to phone,
                "contactEmail" to email,
                "contactName" to contactPerson,
                "notes" to null,
                "createdBy" to createdBy,
                "isActive" to true,
                "createdAt" to now,
                "updatedAt" to now
            )

            val docRef = firestore.collection(CUSTOMERS_COLLECTION).document()
            val customerId = docRef.id
            docRef.set(customerData)
            Log.d(TAG, "Customer creation queued: $customerId")

            // Cache locally
            val customerEntity = CustomerEntity(
                id = customerId,
                name = name,
                address = address,
                phone = phone,
                email = email,
                contactPerson = contactPerson,
                isActive = true,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                isSynced = false
            )
            customerDao.insertCustomer(customerEntity)

            CustomerResult.Success(customerEntity.toDomainModel())
        } catch (e: Exception) {
            Log.e(TAG, "Error creating customer", e)
            CustomerResult.Error("Failed to create customer: ${e.localizedMessage}")
        }
    }

    /**
     * Get customer by ID from local cache.
     */
    suspend fun getCustomerById(id: String): Customer? {
        return customerDao.getCustomerById(id)?.toDomainModel()
    }
}

private fun CustomerEntity.toDomainModel(): Customer {
    return Customer(
        id = id,
        name = name,
        address = address,
        phone = phone,
        email = email,
        contactPerson = contactPerson,
        isActive = isActive
    )
}
