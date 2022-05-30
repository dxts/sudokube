#include <string>
#include <filesystem>
#include <queue>
#include "Utility.h"
#include "ByteMinHeap.h"

struct Metadata {
    const size_t RowsCount;
    unsigned int ColumnSize; 
    const size_t PageRowsCount;
    unsigned int DestinationKeySize;
    const unsigned int ValueSize;
};

// return a pointer the specified column from the on memory column store 
inline byte *getColumn(byte **Array, size_t Idx, size_t OldColumnSize) {
    byte *Column = (byte *)Array + OldColumnSize * Idx;
    return Column;
}

inline void printColumnBit(int pos, byte *column) {
    int b = (column[pos / 8] >> (pos % 8)) % 2;
    if(b) printf("1");
    else  printf("0");
}

// return True if the given bit is 1
inline bool getBit(byte* Array, int Position) {
    unsigned int BytePos = Position >> 3;  
    unsigned int BitPos = Position & 0x7;

    return Array[BytePos] & 1 << BitPos;
}

// set the bit to 1
inline void setBit(byte*& Array, int Position) {
    unsigned int BytePos = Position >> 3;  
    unsigned int BitPos = Position & 0x7;

    Array[BytePos] |= 1 << BitPos;
}

// read keys from column store
inline void readKeys(unsigned int MaskSum, unsigned int Mask[], int Iteration, std::string CuboidDirPath, const size_t BufferRowsCount, byte **IntermediateKeyStore, unsigned int BufferColumnSize) {
    for(int j=0; j<MaskSum; j++){
        std::string ColReadFileName = CuboidDirPath + "col" + std::to_string(Mask[j]);
        FILE* ColReadFile = fopen (ColReadFileName.c_str(), "r");
        assert(ColReadFile != NULL);
        fseek(ColReadFile, Iteration*BufferRowsCount/8, SEEK_SET);
        fread(getColumn(IntermediateKeyStore, j, BufferColumnSize), sizeof(byte), BufferColumnSize, ColReadFile);
        fclose (ColReadFile);
    }
}

// reads values from column store
inline void readValues(std::string CuboidDirPath, int Iteration, const size_t BufferRowsCount, value_t *IntermediateValStore) {
    std::string ValueFileName = CuboidDirPath + "values";
    FILE *ReadValueFile = fopen(ValueFileName.c_str(), "r");
    assert(ReadValueFile != NULL);
    fseek(ReadValueFile, Iteration*BufferRowsCount*sizeof(value_t), SEEK_SET);
    fread(IntermediateValStore, sizeof(value_t), BufferRowsCount, ReadValueFile);
    fclose(ReadValueFile);
}

inline Metadata getMetadata(std::string CubeDirPath, const unsigned int MaskSum) {
    std::string MetadataFilePath = CubeDirPath + "/metadata";

    byte *MetadataOnDisk = (byte*)calloc(MetadataSizeOnDisk, sizeof(byte));

    FILE* MetadataFile = fopen (MetadataFilePath.c_str(), "r");
    assert(MetadataFile != NULL);
    fread(MetadataOnDisk, sizeof(byte), MetadataSizeOnDisk, MetadataFile);
    fclose(MetadataFile);

    const size_t OldRowsCount = *(size_t*)(MetadataOnDisk + sizeof(IsDenseType));
    unsigned int OldColumnSize = bitsToBytes(OldRowsCount);

    unsigned int DestinationKeySize = bitsToBytes(MaskSum);
    const unsigned int ValueSize = sizeof(value_t);
    
    // number of key value pairs that can fit in a page at a time
    const size_t PageRowsCount = ((PageSize / (DestinationKeySize + ValueSize)) >> 3 ) << 3;
    
    Metadata Metadata = {OldRowsCount, OldColumnSize, PageRowsCount, DestinationKeySize, ValueSize};

    printf("IsDense: %hhd\n", *(IsDenseType*)(MetadataOnDisk));
    printf("OldRowsCount: %zu\n", OldRowsCount);
    printf("OldColumnSize: %d\n", OldColumnSize);

    printf("PageRowsCount: %zu\n", PageRowsCount);

    free(MetadataOnDisk);

    return Metadata;
}




inline void printKeyValuePairs(const size_t RowsCount, unsigned int MaskSum, unsigned int KeySize, byte *KeysArray, value_t *ValuesArray) {
    for (size_t i = 0; i<RowsCount; i++) { 
        print_key(MaskSum, getKeyFromKeysArray(KeysArray, i, KeySize));
        printf(" : %lld", *getValueFromValueArray(ValuesArray, i));
        printf("\n");
    }
}

byte *getKeyFromKeyValArray(byte *array, size_t idx, size_t keySize, size_t valSize) {
    byte *key = array + (keySize+valSize) * idx;
    // printf("array = %d idx = %d recSize = %d key = %d\n", array, idx, recSize, key);
    return key;
}

value_t *getValueFromKeyValArray(byte *array, size_t idx, size_t keySize, size_t valSize) {
    value_t *val = (value_t*)(array + (keySize+valSize) * idx + keySize);
    // printf("array = %d idx = %d recSize = %d key = %d\n", array, idx, recSize, key);
    return val;
}

inline void printKeyValuePairsFromKeyValArray(const size_t RowsCount, unsigned int MaskSum, unsigned int KeySize, unsigned int ValueSize, byte *KeyValueArray) {
    for (size_t i = 0; i<RowsCount; i++) { 
        print_key(MaskSum, getKeyFromKeyValArray(KeyValueArray, i, KeySize, ValueSize));
        printf(" : %lld", (value_t)*getValueFromKeyValArray(KeyValueArray, i, KeySize, ValueSize));
        printf("\n");
    }
}

inline size_t MergeKeysInPlace(const size_t BufferRowsCount, unsigned int KeySize, unsigned int ValueSize, byte *KeysArray, value_t *ValuesArray, byte* KeyValueArray) {
    size_t j = 0; // index of last elemet
    memcpy(getKeyFromKeyValArray(KeyValueArray, j, KeySize, ValueSize), getKeyFromKeysArray(KeysArray, 0, KeySize), KeySize);
    memcpy(getValueFromKeyValArray(KeyValueArray, j, KeySize, ValueSize), getValueFromValueArray(ValuesArray, 0), ValueSize);
    for (size_t i = 1; i<BufferRowsCount; i++) {
        if (memcmp(getKeyFromKeyValArray(KeyValueArray, j, KeySize, ValueSize), getKeyFromKeysArray(KeysArray, i, KeySize), KeySize) == 0) {
            *getValueFromKeyValArray(KeyValueArray, j, KeySize, ValueSize) += *getValueFromValueArray(ValuesArray, i);
        } else {
            j++;
            memcpy(getKeyFromKeyValArray(KeyValueArray, j, KeySize, ValueSize), getKeyFromKeysArray(KeysArray, i, KeySize), KeySize);
            memcpy(getValueFromKeyValArray(KeyValueArray, j, KeySize, ValueSize), getValueFromValueArray(ValuesArray, i), ValueSize);
        }
    }
    return j + 1; // returns the new length of the array after merge
}

inline void ExternalMerge(unsigned int NumberOfRuns, std::string RunFileNamePrefix, std::string RunFileNameSuffix, const size_t PageRowsCount, unsigned int KeySize, unsigned int ValueSize, unsigned int MaskSum) {
    // K = (BufferPages-1) - Way Merge
    unsigned int K = BufferPages - 1;
    #ifdef DEBUG
    printf("\nExternal Merge\n");
    printf("%d - Way\n", K);
    printf("Page Rows Count: %zu\n", PageRowsCount);
    printf("NumberOfRuns: %d\n", NumberOfRuns);
    #endif

    // start with pass = 0
    unsigned int CurrentPass = 0;
    unsigned int NumberOfInputRuns = NumberOfRuns;
    while (NumberOfInputRuns > 1) { // passes
        #ifdef DEBUG
        printf("\n\n\nPass %d\n", CurrentPass);
        #endif

        // number of the next pass
        unsigned int NextPass = CurrentPass + 1;

        // current output run being processed for the next pass (start with run 0)
        unsigned int CurrentOutputRun = 0;

        for (int runs = 0; runs < NumberOfInputRuns; runs += K) { // one iteration produce signle run
            printf("\n\nOutput Run %d\n", CurrentOutputRun);
            int RunsUpperBound = std::min(runs+K, NumberOfInputRuns);

            // number of runs to merge
            int NumberOfRunsToMerge = RunsUpperBound-runs;
            
            // elements left to be merged in each run
            size_t *RemainingRowsInInputRuns = (size_t *)calloc(NumberOfRunsToMerge, sizeof(size_t)); // update at the end of a block
            
            // buffer pages
            byte *InputKeyValueBufferPages = (byte*)calloc(NumberOfRunsToMerge, PageSize);

            // elements left to be processed in the buffer page
            size_t *TotalRowsInInputBuffer = (size_t *)calloc(NumberOfRunsToMerge, sizeof(size_t));

            // current element being processed in the buffer page
            size_t *CurrentRowInInputBuffer = (size_t *)calloc(NumberOfRunsToMerge, sizeof(size_t)); // update after processing each row

            // stores file pointers of each file to merge
            //FILE* FilePointers = (FILE*)calloc(NumberOfRunsToMerge, sizeof(FILE));;
            std::vector<FILE*> FilePointers;
            
            // min heap based priority queue to get the minimum from all sorted pages in memory
            MinHeap PriorityQueue(NumberOfRunsToMerge, KeySize);

            // opens the file for each input run, loads their first pages into the buffer, and pushes the first element into the priority queue
            for (int run = 0; run < NumberOfRunsToMerge; run++) {
                // open the file corresponding to the run and add it to the file pointers array
                std::string RunFileName = RunFileNamePrefix + std::to_string(CurrentPass) + "_" + std::to_string(runs+run) + RunFileNameSuffix;
                FILE *RunFile = fopen(RunFileName.c_str(), "rb");
                assert(RunFile != NULL);
                // memcpy(FilePointers + run, RunFile, sizeof(FILE));
                FilePointers.push_back(RunFile);

                // read the number of rows (key value pairs) in the run
                fread(RemainingRowsInInputRuns + (run), sizeof(size_t), 1, FilePointers[run]);
                size_t RunRowsCount = *(RemainingRowsInInputRuns + run);

                // determine the number of rows to read into the buffer from the run
                size_t RowsToRead = std::min(RunRowsCount, PageRowsCount);
                memcpy(TotalRowsInInputBuffer + run, &RowsToRead, sizeof(size_t));

                // read in the key value pairs into the buffer pages
                fread(InputKeyValueBufferPages + run * PageSize, KeySize + ValueSize, RowsToRead, FilePointers[run]);
                PriorityQueue.push(getKeyFromKeyValArray(InputKeyValueBufferPages + run * PageSize, *(CurrentRowInInputBuffer + run), KeySize, ValueSize), run);

                #ifdef DEBUG
                // printf("\nRunRowsCount: %zu\n", RunRowsCount);
                // printf("RowsToRead: %zu\n", RowsToRead);
                // printf("TotalRowsInInputBuffer[run]: %zu\n", RemainingRowsInInputRuns[run]);
                
                // printKeyValuePairsFromKeyValArray(RowsToRead, MaskSum, KeySize, ValueSize, InputKeyValueBufferPages + run * PageSize);
                // printf("\n");
                #endif
            }

            // open the output run file
            std::string OutputRunFileName = RunFileNamePrefix + std::to_string(NextPass) + "_" + std::to_string(CurrentOutputRun) + RunFileNameSuffix;
            FILE *OutputRunFile = fopen(OutputRunFileName.c_str(), "wb");
            assert(OutputRunFile != NULL);

            // write place holder value for output run length
            size_t OutputRunLength = 0;
            fwrite(&OutputRunLength, sizeof(size_t), 1, OutputRunFile);

            // output: allocate one buffer page: stores ( [Key, Value] * PageRowsCount ) key value pairs
            byte *OutputKeyValueBufferPage = (byte*)calloc(1, PageSize);
            // index to write the output to in the buffer page
            size_t CurrentOuputBufferRow = 0;
            // bool ZeroFound = false;
            // number of records accumulated in the buffer
            size_t TotalAccumulatedRowsInCurrentOuputBuffer = 0; 


            // int TotalRows = 0;
            while (PriorityQueue.getSize() != 0) {
                // get the last key and value in output buffer 
                byte* CurrentOuputKey = getKeyFromKeyValArray(OutputKeyValueBufferPage, CurrentOuputBufferRow, KeySize, ValueSize);
                value_t *CurrentOutputValue = getValueFromKeyValArray(OutputKeyValueBufferPage, CurrentOuputBufferRow, KeySize, ValueSize);
                
                // page/run number of minimum value
                pagenumbertype MinRun = PriorityQueue.pop();

                // get the key from run
                byte* MinRunKey = getKeyFromKeyValArray(InputKeyValueBufferPages + MinRun * PageSize, *(CurrentRowInInputBuffer + MinRun), KeySize, ValueSize);
                value_t* MinRunValue = getValueFromKeyValArray(InputKeyValueBufferPages + MinRun * PageSize, *(CurrentRowInInputBuffer + MinRun), KeySize, ValueSize);

                #ifdef DEBUG
                // printf("MinRun : %d\n", MinRun);
                // printf("Key : ");
                // print_key(MaskSum, MinRunKey);
                // printf("\n");
                // printf("MinRunValue : %lld\n", *MinRunValue);
                #endif

                // // special condition to check if key '0' has been found so far in the buffer page
                // if (memoryIsAllZeroes(MinRunKey, KeySize)) { ZeroFound = true; } 

                // compare keys
                if (memcmp(CurrentOuputKey, MinRunKey, KeySize) == 0) {
                    // don't increment, add the values
                    *CurrentOutputValue += *MinRunValue;
                } else {
                    // if output buffer is full write it to the file
                    if (CurrentOuputBufferRow + 1 == PageRowsCount) {
                        #ifdef DEBUG
                        printf("\nOutput Block\n");
                        printKeyValuePairsFromKeyValArray(PageRowsCount, MaskSum, KeySize, ValueSize, OutputKeyValueBufferPage);
                        #endif
                        fwrite(OutputKeyValueBufferPage, KeySize + ValueSize, PageRowsCount, OutputRunFile);
                        OutputRunLength += PageRowsCount;
                        memset(OutputKeyValueBufferPage, 0, PageSize);
                        
                        CurrentOuputBufferRow = 0;
                        TotalAccumulatedRowsInCurrentOuputBuffer = 0;

                        CurrentOuputKey = getKeyFromKeyValArray(OutputKeyValueBufferPage, CurrentOuputBufferRow, KeySize, ValueSize);
                        CurrentOutputValue = getValueFromKeyValArray(OutputKeyValueBufferPage, CurrentOuputBufferRow, KeySize, ValueSize);
                    }
                    // don't incement if it's the first value being written to the buffer
                    else if (TotalAccumulatedRowsInCurrentOuputBuffer != 0) {
                        CurrentOuputBufferRow++;
                        
                        CurrentOuputKey = getKeyFromKeyValArray(OutputKeyValueBufferPage, CurrentOuputBufferRow, KeySize, ValueSize);
                        CurrentOutputValue = getValueFromKeyValArray(OutputKeyValueBufferPage, CurrentOuputBufferRow, KeySize, ValueSize);
                    }  
                    *CurrentOutputValue = *MinRunValue;
                    memcpy(CurrentOuputKey, MinRunKey, KeySize);
                }

                // update the data structures after writing the input to the output buffer
                TotalAccumulatedRowsInCurrentOuputBuffer++;
                *(CurrentRowInInputBuffer + MinRun) += 1;

                // fetch next block for the input run if all rows in the buffer are processed
                if (*(CurrentRowInInputBuffer + MinRun) == *(TotalRowsInInputBuffer + MinRun)) {
                    // update remaining rows to read in the run
                    *(RemainingRowsInInputRuns + MinRun) -= *(TotalRowsInInputBuffer + MinRun);

                    // determine the number of rows to read into the buffer from the run
                    size_t RowsToRead = std::min(*(RemainingRowsInInputRuns + MinRun), PageRowsCount);
                    memcpy(TotalRowsInInputBuffer + MinRun, &RowsToRead, sizeof(size_t));

                    if (RowsToRead > 0) {
                        // read the page 
                        fread(InputKeyValueBufferPages + MinRun * PageSize, KeySize + ValueSize, RowsToRead, FilePointers[MinRun]);

                        // update the current row to read
                        *(CurrentRowInInputBuffer + MinRun) = 0;

                        // push the next element to the priority queue
                        PriorityQueue.push(getKeyFromKeyValArray(InputKeyValueBufferPages + MinRun * PageSize, *(CurrentRowInInputBuffer + MinRun), KeySize, ValueSize), MinRun);
                    }
                } else { // implies  -->  *(CurrentRowInInputBuffer + MinRun) < *(TotalRowsInInputBuffer + MinRun)
                    // printf("priority Queue Push\n");
                    // push the next element to the priority queue
                    PriorityQueue.push(getKeyFromKeyValArray(InputKeyValueBufferPages + MinRun * PageSize, *(CurrentRowInInputBuffer + MinRun), KeySize, ValueSize), MinRun);
                }
            }

            // remiander of the output is written to the file
            fwrite(OutputKeyValueBufferPage, KeySize + ValueSize, CurrentOuputBufferRow + 1, OutputRunFile);
            OutputRunLength += CurrentOuputBufferRow + 1;
            #ifdef DEBUG
            printf("\nOutput Block\n");
            printKeyValuePairsFromKeyValArray(CurrentOuputBufferRow + 1, MaskSum, KeySize, ValueSize, OutputKeyValueBufferPage);
            #endif
        
            // closing files and deallocating memory
            for (int run = 0; run < NumberOfInputRuns; run++) {
                fclose(FilePointers[run]);
            }

            rewind(OutputRunFile);
            fwrite(&OutputRunLength, sizeof(size_t), 1, OutputRunFile);
            fclose(OutputRunFile);

            //free(FilePointers);
            free(RemainingRowsInInputRuns);
            free(InputKeyValueBufferPages);

            // increment the output run number
            CurrentOutputRun++;
        } // new run should be produced before this
        

        NumberOfInputRuns = CurrentOutputRun; 
        // update current pass
        CurrentPass = NextPass;
    }
}