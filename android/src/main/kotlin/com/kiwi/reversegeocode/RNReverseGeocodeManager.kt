package com.kiwi.reversegeocode

import android.location.Geocoder
import android.location.Address
import android.app.Activity
import android.text.TextUtils
import android.util.Log

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableNativeArray
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.WritableNativeMap

import java.util.Locale
import java.util.concurrent.Executors
import java.lang.Runnable

import kotlin.collections.List


class RNReverseGeocodeManager(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

  private val executorService = Executors.newCachedThreadPool();

  companion object {
    const val MAX_RESULTS = 10

    fun getLocationFromAddress(address: Address): WritableMap {
      val location = WritableNativeMap()
      location.putDouble("latitude", address.getLatitude())
      location.putDouble("longitude", address.getLongitude())
      return location
    }

    fun getFirstLineOfAddress(address: Address): String {
      if (address.getMaxAddressLineIndex() > -1) {
        return address.getAddressLine(0)
      }
      return ""
    }

    fun formatAddress(address: Address): WritableMap {
      val addressObject = WritableNativeMap()

      addressObject.putString("name", address.getFeatureName())
      addressObject.putMap("location", getLocationFromAddress(address))
      addressObject.putString("address", getFirstLineOfAddress(address))

      return addressObject
    }

    fun formatAddresses(addresses: List<Address>): WritableArray {
      val formattedAddresses = WritableNativeArray()

      for (address in addresses) {
        formattedAddresses.pushMap(formatAddress(address))
      }

      return formattedAddresses
    }
  }

  override fun getName(): String {
    return "RNReverseGeocode"
  }

  @ReactMethod
  fun searchForLocations(
    searchText: String,
    region: ReadableMap,
    callback: Callback
  ) {
    val currentActivity = getCurrentActivity()

    if (currentActivity == null) {
      callback.invoke("Activity doesn't exist", null)
      return
    }

    if (!Geocoder.isPresent()) {
      callback.invoke("Geocoder is not present", null)
      return
    }

    executorService.execute(Runnable {
      Log.d(getName(), "SearchForLocation: " + searchText + "...")
      val latitude = region.getDouble("latitude")
      val longitude = region.getDouble("longitude")
      val latitudeDelta = region.getDouble("latitudeDelta")
      val longitudeDelta = region.getDouble("longitudeDelta")

      val lowerLeftLatitude = latitude - Math.abs(latitudeDelta)
      val lowerLeftLongitude = longitude - Math.abs(longitudeDelta)
      val upperRightLatitude = latitude + Math.abs(latitudeDelta)
      val upperRightLongitude = longitude + Math.abs(longitudeDelta)

      try {
        val geocoder = Geocoder(reactApplicationContext, Locale.getDefault())

        // First fetch as many local addresses as possible
        Log.d(getName(), "SearchForLocation: local...")
        val localAddresses = geocoder.getFromLocationName(
          searchText,
          MAX_RESULTS,
          lowerLeftLatitude,
          lowerLeftLongitude,
          upperRightLatitude,
          upperRightLongitude
        )
        var addresses = localAddresses.orEmpty()

        // If less than the max-amount of items have been found,
        // then add other more remote possibilities
        if ((localAddresses != null) && (localAddresses.size < MAX_RESULTS)) {
          Log.d(getName(), "SearchForLocation: remote...")
          var remoteAddresses = geocoder.getFromLocationName(
            searchText,
            MAX_RESULTS
          )
          if (remoteAddresses != null) {

            // Filter out local-search duplicates
            remoteAddresses = remoteAddresses.filter { remoteAddress -> localAddresses.find { localAddress -> localAddress.getLatitude() == remoteAddress.getLatitude() && localAddress.getLongitude() == remoteAddress.getLongitude() } == null };

            // Limit the remove search results to not exceed MAX_RESULTS
            if ((remoteAddresses.size + localAddresses.size) > MAX_RESULTS) {
              remoteAddresses = remoteAddresses.subList(0, MAX_RESULTS - localAddresses.size);
            }

            // Concatenate addresses
            addresses = localAddresses + remoteAddresses
          }
        }

        Log.d(getName(), "SearchForLocation, results: " + addresses.size);
        callback.invoke(null, formatAddresses(addresses))
        Log.d(getName(), "SearchForLocation... DONE");
      } catch (e: Exception) {
        callback.invoke(e.message, null)
      }
    });
  }
}
