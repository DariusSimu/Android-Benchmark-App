#include <jni.h>
#include <time.h>
#include <math.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include <unistd.h>

/*
 * -----------------------------------------------------------
 * **Single-Core Pi calculation Chudnovsky**
 * -----------------------------------------------------------
 */

long double factorial(int n) {
    long double result = 1.0;
    for (int i = 2; i <= n; i++)
        result *= i;
    return result;
}

long double compute_pi_chudnovsky(int iterations) {
    long double sum = 0.0;

    for (int k = 0; k < iterations; k++) {
        long double num = (long double)(factorial(6*k)) *
                          (13591409.0 + 545140134.0 * k);
        long double den = (long double)(factorial(3*k)) *
                          pow(factorial(k), 3) *
                          pow(-640320.0, 3*k);
        sum += num / den;
    }

    long double pi = (426880.0 * sqrtl(10005.0)) / sum;
    return pi;
}

JNIEXPORT jdouble JNICALL
Java_com_example_benchmarkandroid_TestsFragment_computeSinglecoreBenchmark(JNIEnv *env, jobject thiz) {
    clock_t start = clock();

    compute_pi_chudnovsky(7500);

    clock_t end = clock();

    long double seconds = (long double)(end - start) / CLOCKS_PER_SEC;

    return (jdouble) seconds;
}

/*
 * -----------------------------------------------------------
 * **Multi-Core Matrix multiplication**
 * -----------------------------------------------------------
 */

typedef struct {
    int start_row;
    int end_row;
    int size;
    float** matrixA;
    float** matrixB;
    float** result;
} MatrixThreadData;

void* matrix_multiply_thread(void* arg) {
    MatrixThreadData* data = (MatrixThreadData*)arg;

    for (int i = data->start_row; i < data->end_row; i++) {
        for (int j = 0; j < data->size; j++) {
            float sum = 0.0f;
            for (int k = 0; k < data->size; k++) {
                sum += data->matrixA[i][k] * data->matrixB[k][j];
            }
            data->result[i][j] = sum;
        }
    }

    return NULL;
}

float** allocate_matrix(int size) {
    float** matrix = (float**)malloc(size * sizeof(float*));
    for (int i = 0; i < size; i++) {
        matrix[i] = (float*)malloc(size * sizeof(float));
        for (int j = 0; j < size; j++) {
            matrix[i][j] = (float)(rand() % 100) / 10.0f;
        }
    }
    return matrix;
}

void free_matrix(float** matrix, int size) {
    for (int i = 0; i < size; i++) {
        free(matrix[i]);
    }
    free(matrix);
}

long double matrix_multiply_multicore(int size, int num_threads) {
    float** matrixA = allocate_matrix(size);
    float** matrixB = allocate_matrix(size);
    float** result = allocate_matrix(size);

    pthread_t* threads = (pthread_t*)malloc(num_threads * sizeof(pthread_t));
    MatrixThreadData* thread_data = (MatrixThreadData*)malloc(num_threads * sizeof(MatrixThreadData));

    int rows_per_thread = size / num_threads;

    for (int i = 0; i < num_threads; i++) {
        thread_data[i].start_row = i * rows_per_thread;
        thread_data[i].end_row = (i == num_threads - 1) ? size : (i + 1) * rows_per_thread;
        thread_data[i].size = size;
        thread_data[i].matrixA = matrixA;
        thread_data[i].matrixB = matrixB;
        thread_data[i].result = result;

        pthread_create(&threads[i], NULL, matrix_multiply_thread, &thread_data[i]);
    }

    for (int i = 0; i < num_threads; i++) {
        pthread_join(threads[i], NULL);
    }

    free(threads);
    free(thread_data);
    free_matrix(matrixA, size);
    free_matrix(matrixB, size);
    free_matrix(result, size);

    return 0.0;
}

JNIEXPORT jdouble JNICALL
Java_com_example_benchmarkandroid_TestsFragment_computeMulticoreBenchmark(JNIEnv *env, jobject thiz) {
    int num_cores = (int)sysconf(_SC_NPROCESSORS_ONLN);

    clock_t start = clock();

    matrix_multiply_multicore(1000, num_cores);

    clock_t end = clock();

    long double seconds = (long double)(end - start) / CLOCKS_PER_SEC;

    return (jdouble) seconds;
}

JNIEXPORT jint JNICALL
Java_com_example_benchmarkandroid_TestsFragment_getCoreCount(JNIEnv *env, jobject thiz) {
    return (jint)sysconf(_SC_NPROCESSORS_ONLN);
}

/*
 * -----------------------------------------------------------
 * **Memory bandwidth**
 * -----------------------------------------------------------
 */

double memory_bandwidth_test(size_t buffer_size, int duration_seconds) {
    char* source = (char*)malloc(buffer_size);
    char* dest = (char*)malloc(buffer_size);

    if (source == NULL || dest == NULL) {
        if (source) free(source);
        if (dest) free(dest);
        return -1.0;
    }

    memset(source, 0xAA, buffer_size);

    clock_t start = clock();
    clock_t end_time = start + (duration_seconds * CLOCKS_PER_SEC);
    long long bytes_copied = 0;

    while (clock() < end_time) {
        memcpy(dest, source, buffer_size);
        bytes_copied += buffer_size;
    }

    clock_t end = clock();

    free(source);
    free(dest);

    double seconds = (double)(end - start) / CLOCKS_PER_SEC;

    double bandwidth_gbps = (bytes_copied / seconds) / (1024.0 * 1024.0 * 1024.0);

    return bandwidth_gbps;
}

JNIEXPORT jdouble JNICALL
Java_com_example_benchmarkandroid_TestsFragment_computeMemoryBenchmark(JNIEnv *env, jobject thiz) {
    size_t buffer_size = 100 * 1024 * 1024;
    int duration_seconds = 5;

    double bandwidth = memory_bandwidth_test(buffer_size, duration_seconds);

    return (jdouble)bandwidth;
}

/*
 * -----------------------------------------------------------
 * **Storage throughput**
 * -----------------------------------------------------------
 */

double storage_write_test(const char* file_path, size_t block_size, int duration_seconds) {
    FILE* file = fopen(file_path, "wb");

    if (file == NULL) {
        return -1.0;
    }

    char* buffer = (char*)malloc(block_size);
    if (buffer == NULL) {
        fclose(file);
        return -1.0;
    }

    memset(buffer, 0x55, block_size);

    clock_t start = clock();
    clock_t end_time = start + (duration_seconds * CLOCKS_PER_SEC);
    long long bytes_written = 0;

    while (clock() < end_time) {
        size_t written = fwrite(buffer, 1, block_size, file);
        if (written != block_size) {
            break;
        }
        bytes_written += written;
    }

    fflush(file);

    clock_t end = clock();

    fclose(file);
    remove(file_path);
    free(buffer);

    double seconds = (double)(end - start) / CLOCKS_PER_SEC;

    double throughput_mbps = (bytes_written / seconds) / (1024.0 * 1024.0);

    return throughput_mbps;
}

JNIEXPORT jdouble JNICALL
Java_com_example_benchmarkandroid_TestsFragment_computeStorageBenchmark(JNIEnv *env, jobject thiz, jstring file_path) {
    const char* path = (*env)->GetStringUTFChars(env, file_path, NULL);

    size_t block_size = 1 * 512 * 512;
    int duration_seconds = 5;

    double throughput = storage_write_test(path, block_size, duration_seconds);

    (*env)->ReleaseStringUTFChars(env, file_path, path);

    return (jdouble)throughput;
}